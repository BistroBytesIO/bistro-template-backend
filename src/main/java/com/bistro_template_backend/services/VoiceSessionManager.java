package com.bistro_template_backend.services;

import com.bistro_template_backend.config.VoiceAIConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class VoiceSessionManager {

    private final VoiceAIConfig voiceAIConfig;
    
    // In-memory session storage (in production, consider using Redis or database)
    private final Map<String, VoiceSession> activeSessions = new ConcurrentHashMap<>();

    /**
     * Create a new voice session
     */
    public String createSession(String customerId, String customerEmail) {
        String sessionId = UUID.randomUUID().toString();
        
        VoiceSession session = VoiceSession.builder()
                .sessionId(sessionId)
                .customerId(customerId)
                .customerEmail(customerEmail)
                .createdAt(LocalDateTime.now())
                .lastActivityAt(LocalDateTime.now())
                .conversationHistory(new ArrayList<>())
                .currentOrder(new VoiceOrder())
                .turnCount(0)
                .active(true)
                .build();
        
        activeSessions.put(sessionId, session);
        log.info("Created new voice session: {} for customer: {}", sessionId, customerId);
        
        return sessionId;
    }

    /**
     * Get voice session by ID
     */
    @Cacheable(value = "voice-context-cache", key = "#sessionId")
    public VoiceSession getSession(String sessionId) {
        VoiceSession session = activeSessions.get(sessionId);
        if (session == null) {
            throw new IllegalArgumentException("Voice session not found: " + sessionId);
        }
        
        // Check if session has expired
        if (isSessionExpired(session)) {
            closeSession(sessionId, "Session expired");
            throw new IllegalArgumentException("Voice session has expired: " + sessionId);
        }
        
        return session;
    }

    /**
     * Update session activity and add conversation turn
     */
    public void addConversationTurn(String sessionId, String userMessage, String aiResponse) {
        VoiceSession session = getSession(sessionId);
        
        // Check turn limit
        if (session.getTurnCount() >= voiceAIConfig.getMaxSessionTurns()) {
            closeSession(sessionId, "Maximum conversation turns reached");
            throw new IllegalArgumentException("Session turn limit exceeded");
        }
        
        // Add to conversation history
        session.getConversationHistory().add("USER: " + userMessage);
        session.getConversationHistory().add("ASSISTANT: " + aiResponse);
        
        // Update session metadata
        session.setLastActivityAt(LocalDateTime.now());
        session.setTurnCount(session.getTurnCount() + 1);
        
        // Trim conversation history if it gets too long
        trimConversationHistory(session);
        
        log.debug("Added conversation turn to session: {}. Total turns: {}", 
                sessionId, session.getTurnCount());
    }

    /**
     * Update order context in session
     */
    public void updateOrderContext(String sessionId, VoiceOrder order) {
        VoiceSession session = getSession(sessionId);
        session.setCurrentOrder(order);
        session.setLastActivityAt(LocalDateTime.now());
        
        log.debug("Updated order context for session: {}", sessionId);
    }

    /**
     * Get conversation context for AI processing
     */
    public String getConversationContext(String sessionId) {
        VoiceSession session = getSession(sessionId);
        return String.join("\n", session.getConversationHistory());
    }

    /**
     * Get order context as formatted string
     */
    public String getOrderContext(String sessionId) {
        VoiceSession session = getSession(sessionId);
        VoiceOrder order = session.getCurrentOrder();
        
        if (order.getItems().isEmpty()) {
            return "Current Order: Empty";
        }
        
        StringBuilder context = new StringBuilder("Current Order:\n");
        for (VoiceOrderItem item : order.getItems()) {
            context.append("- ").append(item.getQuantity())
                   .append("x ").append(item.getName())
                   .append(" ($").append(item.getPrice()).append(")");
            
            if (!item.getCustomizations().isEmpty()) {
                context.append(" with ").append(String.join(", ", item.getCustomizations()));
            }
            context.append("\n");
        }
        
        if (order.getSubtotal() != null) {
            context.append("Subtotal: $").append(order.getSubtotal()).append("\n");
            context.append("Total: $").append(order.getTotal());
        }
        
        return context.toString();
    }

    /**
     * Close voice session
     */
    @CacheEvict(value = "voice-context-cache", key = "#sessionId")
    public void closeSession(String sessionId, String reason) {
        VoiceSession session = activeSessions.get(sessionId);
        if (session != null) {
            session.setActive(false);
            session.setEndedAt(LocalDateTime.now());
            session.setEndReason(reason);
            
            activeSessions.remove(sessionId);
            log.info("Closed voice session: {} - Reason: {}", sessionId, reason);
        }
    }

    /**
     * Get all active sessions for monitoring
     */
    public List<VoiceSession> getActiveSessions() {
        return new ArrayList<>(activeSessions.values());
    }

    /**
     * Get session statistics
     */
    public Map<String, Object> getSessionStatistics() {
        List<VoiceSession> sessions = getActiveSessions();
        
        return Map.of(
            "activeSessionCount", sessions.size(),
            "averageTurnsPerSession", sessions.stream()
                .mapToInt(VoiceSession::getTurnCount)
                .average()
                .orElse(0.0),
            "oldestSessionAge", sessions.stream()
                .map(s -> java.time.Duration.between(s.getCreatedAt(), LocalDateTime.now()).toMinutes())
                .max(Long::compareTo)
                .orElse(0L)
        );
    }

    /**
     * Scheduled cleanup of expired sessions
     */
    @Scheduled(fixedRate = 300000) // Every 5 minutes
    public void cleanupExpiredSessions() {
        List<String> expiredSessions = new ArrayList<>();
        
        for (Map.Entry<String, VoiceSession> entry : activeSessions.entrySet()) {
            if (isSessionExpired(entry.getValue())) {
                expiredSessions.add(entry.getKey());
            }
        }
        
        for (String sessionId : expiredSessions) {
            closeSession(sessionId, "Session timeout");
        }
        
        if (!expiredSessions.isEmpty()) {
            log.info("Cleaned up {} expired voice sessions", expiredSessions.size());
        }
    }

    /**
     * Check if session has expired
     */
    private boolean isSessionExpired(VoiceSession session) {
        LocalDateTime expireTime = session.getLastActivityAt()
                .plusNanos(voiceAIConfig.getSessionTimeout() * 1_000_000);
        return LocalDateTime.now().isAfter(expireTime);
    }

    /**
     * Trim conversation history to keep within context window
     */
    private void trimConversationHistory(VoiceSession session) {
        List<String> history = session.getConversationHistory();
        
        // Estimate token count (rough approximation: 1 token = 4 characters)
        int totalTokens = history.stream()
                .mapToInt(s -> s.length() / 4)
                .sum();
        
        // Keep removing oldest entries until we're under the context window
        while (totalTokens > voiceAIConfig.getContextWindow() && history.size() > 2) {
            String removed = history.remove(0);
            totalTokens -= removed.length() / 4;
        }
    }

    /**
     * Voice Session data class
     */
    @lombok.Builder
    @lombok.Data
    public static class VoiceSession {
        private String sessionId;
        private String customerId;
        private String customerEmail;
        private LocalDateTime createdAt;
        private LocalDateTime lastActivityAt;
        private LocalDateTime endedAt;
        private String endReason;
        private List<String> conversationHistory;
        private VoiceOrder currentOrder;
        private int turnCount;
        private boolean active;
        private String language = "en";
        private Map<String, Object> metadata = new HashMap<>();
    }

    /**
     * Voice Order data class
     */
    @lombok.Builder
    @lombok.Data
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class VoiceOrder {
        private List<VoiceOrderItem> items = new ArrayList<>();
        private Double subtotal;
        private Double tax;
        private Double total;
        private String specialInstructions;
        private String paymentMethod;
        private String status = "building";
    }

    /**
     * Voice Order Item data class
     */
    @lombok.Builder
    @lombok.Data
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class VoiceOrderItem {
        private Long menuItemId;
        private String name;
        private Integer quantity;
        private Double price;
        private List<String> customizations = new ArrayList<>();
        private String notes;
    }
}