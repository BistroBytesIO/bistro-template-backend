package com.bistro_template_backend.services;

import com.bistro_template_backend.config.RealtimeConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.security.SecureRandom;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * WebRTC-focused voice service for handling ephemeral tokens and session management
 * Replaces the complex WebSocket proxy logic with direct client-to-OpenAI connections
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class WebRTCVoiceService {

    private final RealtimeConfig realtimeConfig;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate = new RestTemplate();
    private final SecureRandom secureRandom = new SecureRandom();

    // Session management for WebRTC connections
    private final Map<String, WebRTCSession> activeSessions = new ConcurrentHashMap<>();

    // Token validation cache
    private final Map<String, TokenValidation> tokenValidationCache = new ConcurrentHashMap<>();

    // Rate limiting for token generation
    private final Map<String, List<LocalDateTime>> tokenRequestHistory = new ConcurrentHashMap<>();
    private static final int MAX_TOKENS_PER_HOUR = 10;

    /**
     * Generate ephemeral token for direct WebRTC connection to OpenAI with enhanced security
     */
    public String generateEphemeralToken(String customerId, String sessionType) {
        try {
            log.info("Generating ephemeral token for customer: {}, sessionType: {}", customerId, sessionType);

            // Security validations
            validateTokenRequest(customerId, sessionType);

            // Rate limiting check
            if (!checkRateLimit(customerId)) {
                throw new RuntimeException("Rate limit exceeded for customer: " + customerId);
            }

            // Validate API key
            if (realtimeConfig.getApiKey() == null || realtimeConfig.getApiKey().trim().isEmpty()) {
                throw new RuntimeException("OpenAI API key is not configured");
            }

            // Generate session identifier for tracking
            String internalSessionId = generateSecureSessionId();
            String requestId = generateRequestId();

            // Create request for OpenAI ephemeral token
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + realtimeConfig.getApiKey());
            headers.set("Content-Type", "application/json");
            headers.set("User-Agent", "BistroBytes-WebRTC/1.0");
            headers.set("X-Request-ID", requestId);

            // Create minimal request body per OpenAI docs - only model and voice are supported
            Map<String, Object> requestBody = Map.of(
                "model", realtimeConfig.getModel(),
                "voice", realtimeConfig.getVoice()
            );

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            // Call OpenAI API to create ephemeral token
            ResponseEntity<Map> response = restTemplate.exchange(
                "https://api.openai.com/v1/realtime/sessions",
                HttpMethod.POST,
                entity,
                Map.class
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> responseBody = response.getBody();

                // Debug log the complete response structure
                log.info("OpenAI Response Status: {}", response.getStatusCode());
                log.info("OpenAI Response Headers: {}", response.getHeaders());
                log.info("OpenAI Response Body: {}", responseBody);
                log.info("OpenAI Response Body Keys: {}", responseBody.keySet());
                log.info("OpenAI Response Body Values: {}", responseBody.values());

                // Log each key-value pair individually
                for (Map.Entry<String, Object> entry : responseBody.entrySet()) {
                    log.info("Key: '{}', Value: '{}', Value Type: {}",
                        entry.getKey(),
                        entry.getValue(),
                        entry.getValue() != null ? entry.getValue().getClass().getSimpleName() : "null");
                }

                // Simple extraction to see what's actually there
                LinkedHashMap<String, Object> clientSecretHashMap = (LinkedHashMap<String, Object>) responseBody.get("client_secret");
                String token = (String) clientSecretHashMap.get("value");
                String sessionId = (String) responseBody.get("id");

                log.info("Direct extraction - token: {}, sessionId: {}",
                    token != null ? "***" + token.substring(Math.max(0, token.length() - 4)) : "null",
                    sessionId);

                if (token != null && !token.trim().isEmpty()) {
                    // Default expiration time (1 hour from now)
                    long expirationTime = System.currentTimeMillis() / 1000 + 3600;

                    // Store token validation info
                    storeTokenValidation(token, customerId, sessionType, internalSessionId, sessionId, expirationTime);

                    // Update rate limiting
                    updateRateLimit(customerId);

                    log.info("Successfully generated ephemeral token for customer: {} (sessionId: {})",
                        customerId, internalSessionId);
                    return token;
                } else {
                    log.error("No valid token found in OpenAI response");
                    throw new RuntimeException("No valid token returned from OpenAI API");
                }
            } else {
                log.error("OpenAI API error response: {}", response.getBody());
                throw new RuntimeException("Failed to generate ephemeral token: " + response.getStatusCode());
            }

        } catch (Exception e) {
            log.error("Failed to generate ephemeral token for customer: {}", customerId, e);
            throw new RuntimeException("Token generation failed: " + e.getMessage(), e);
        }
    }

    /**
     * Create WebRTC session for tracking and management with order context
     */
    public String createWebRTCSession(String customerId, String sessionType) {
        try {
            String sessionId = UUID.randomUUID().toString();

            // Initialize order context for the session
            OrderContext orderContext = OrderContext.builder()
                .customerId(customerId)
                .sessionId(sessionId)
                .items(new java.util.ArrayList<>())
                .subtotal(0.0)
                .tax(0.0)
                .total(0.0)
                .status("active")
                .createdAt(LocalDateTime.now())
                .build();

            WebRTCSession session = WebRTCSession.builder()
                .sessionId(sessionId)
                .customerId(customerId)
                .sessionType(sessionType)
                .createdAt(LocalDateTime.now())
                .lastActivity(LocalDateTime.now())
                .status("created")
                .orderContext(orderContext)
                .connectionStatus("pending")
                .voiceMetrics(new java.util.HashMap<>())
                .build();

            activeSessions.put(sessionId, session);

            log.info("Created WebRTC session: {} for customer: {} with order context", sessionId, customerId);
            return sessionId;

        } catch (Exception e) {
            log.error("Failed to create WebRTC session for customer: {}", customerId, e);
            throw new RuntimeException("Session creation failed: " + e.getMessage(), e);
        }
    }

    /**
     * Get WebRTC session status with order context
     */
    public Map<String, Object> getWebRTCSessionStatus(String sessionId) {
        WebRTCSession session = activeSessions.get(sessionId);

        if (session == null) {
            return Map.of();
        }

        Map<String, Object> status = new java.util.HashMap<>();
        status.put("sessionId", session.getSessionId());
        status.put("customerId", session.getCustomerId());
        status.put("sessionType", session.getSessionType());
        status.put("status", session.getStatus());
        status.put("connectionStatus", session.getConnectionStatus());
        status.put("createdAt", session.getCreatedAt().toString());
        status.put("lastActivity", session.getLastActivity() != null ? session.getLastActivity().toString() : null);

        // Include order context if available
        if (session.getOrderContext() != null) {
            OrderContext orderContext = session.getOrderContext();
            status.put("orderContext", Map.of(
                "itemCount", orderContext.getItems().size(),
                "subtotal", orderContext.getSubtotal(),
                "tax", orderContext.getTax(),
                "total", orderContext.getTotal(),
                "status", orderContext.getStatus(),
                "items", orderContext.getItems()
            ));
        }

        // Include voice metrics if available
        if (session.getVoiceMetrics() != null && !session.getVoiceMetrics().isEmpty()) {
            status.put("voiceMetrics", session.getVoiceMetrics());
        }

        return status;
    }

    /**
     * Validate session and check if it's active
     */
    public boolean validateSession(String sessionId) {
        WebRTCSession session = activeSessions.get(sessionId);

        if (session == null) {
            log.warn("Session not found: {}", sessionId);
            return false;
        }

        // Check if session has expired
        if (session.getCreatedAt().isBefore(LocalDateTime.now().minusHours(2))) {
            log.warn("Session expired: {}", sessionId);
            activeSessions.remove(sessionId);
            return false;
        }

        // Check if session is in valid status
        List<String> validStatuses = List.of("created", "connected", "active", "ordering");
        if (!validStatuses.contains(session.getStatus())) {
            log.warn("Session in invalid status: {} - {}", sessionId, session.getStatus());
            return false;
        }

        return true;
    }

    /**
     * Update session connection status
     */
    public void updateSessionConnectionStatus(String sessionId, String connectionStatus) {
        WebRTCSession session = activeSessions.get(sessionId);
        if (session != null) {
            session.setConnectionStatus(connectionStatus);
            session.setLastActivity(LocalDateTime.now());

            // Update session status based on connection
            if ("connected".equals(connectionStatus)) {
                session.setStatus("active");
            } else if ("disconnected".equals(connectionStatus) || "failed".equals(connectionStatus)) {
                session.setStatus("ended");
            }

            log.debug("Updated session {} connection status: {}", sessionId, connectionStatus);
        }
    }

    /**
     * Add item to session order context
     */
    public void addItemToOrder(String sessionId, Map<String, Object> item) {
        WebRTCSession session = activeSessions.get(sessionId);
        if (session != null && session.getOrderContext() != null) {
            OrderContext orderContext = session.getOrderContext();
            orderContext.getItems().add(item);

            // Recalculate totals
            recalculateOrderTotals(orderContext);

            session.setLastActivity(LocalDateTime.now());
            session.setStatus("ordering");

            log.info("Added item to order for session {}: {}", sessionId, item.get("name"));
        }
    }

    /**
     * Remove item from session order context
     */
    public void removeItemFromOrder(String sessionId, int itemIndex) {
        WebRTCSession session = activeSessions.get(sessionId);
        if (session != null && session.getOrderContext() != null) {
            OrderContext orderContext = session.getOrderContext();

            if (itemIndex >= 0 && itemIndex < orderContext.getItems().size()) {
                Map<String, Object> removedItem = orderContext.getItems().remove(itemIndex);

                // Recalculate totals
                recalculateOrderTotals(orderContext);

                session.setLastActivity(LocalDateTime.now());

                log.info("Removed item from order for session {}: {}", sessionId, removedItem.get("name"));
            }
        }
    }

    /**
     * Get order summary for session
     */
    public Map<String, Object> getOrderSummary(String sessionId) {
        WebRTCSession session = activeSessions.get(sessionId);
        if (session == null || session.getOrderContext() == null) {
            return Map.of("error", "Session or order context not found");
        }

        OrderContext orderContext = session.getOrderContext();
        return Map.of(
            "sessionId", sessionId,
            "customerId", orderContext.getCustomerId(),
            "items", orderContext.getItems(),
            "itemCount", orderContext.getItems().size(),
            "subtotal", orderContext.getSubtotal(),
            "tax", orderContext.getTax(),
            "total", orderContext.getTotal(),
            "status", orderContext.getStatus(),
            "createdAt", orderContext.getCreatedAt().toString()
        );
    }

    /**
     * Update voice metrics for session
     */
    public void updateVoiceMetrics(String sessionId, String metricName, Object value) {
        WebRTCSession session = activeSessions.get(sessionId);
        if (session != null) {
            session.getVoiceMetrics().put(metricName, value);
            session.setLastActivity(LocalDateTime.now());

            log.debug("Updated voice metric for session {}: {} = {}", sessionId, metricName, value);
        }
    }

    private void recalculateOrderTotals(OrderContext orderContext) {
        double subtotal = 0.0;

        for (Map<String, Object> item : orderContext.getItems()) {
            Double price = (Double) item.get("price");
            Integer quantity = (Integer) item.get("quantity");

            if (price != null && quantity != null) {
                subtotal += price * quantity;
            }
        }

        double tax = subtotal * 0.08; // 8% tax rate
        double total = subtotal + tax;

        orderContext.setSubtotal(subtotal);
        orderContext.setTax(tax);
        orderContext.setTotal(total);
    }

    /**
     * Update session activity
     */
    public void updateSessionActivity(String sessionId, String activity) {
        WebRTCSession session = activeSessions.get(sessionId);
        if (session != null) {
            session.setLastActivity(LocalDateTime.now());
            session.setStatus(activity);
            log.debug("Updated session {} activity: {}", sessionId, activity);
        }
    }

    /**
     * Close WebRTC session
     */
    public void closeWebRTCSession(String sessionId) {
        WebRTCSession session = activeSessions.remove(sessionId);
        if (session != null) {
            log.info("Closed WebRTC session: {} for customer: {}", sessionId, session.getCustomerId());
        }
    }

    /**
     * Get service health status
     */
    public Map<String, Object> getConnectionStatus() {
        try {
            // Test OpenAI API connectivity
            boolean openaiHealthy = testOpenAIConnectivity();

            return Map.of(
                "healthy", openaiHealthy,
                "openaiConnected", openaiHealthy,
                "activeSessions", activeSessions.size(),
                "serviceType", "webrtc",
                "timestamp", LocalDateTime.now().toString()
            );

        } catch (Exception e) {
            log.error("Health check failed", e);
            return Map.of(
                "healthy", false,
                "openaiConnected", false,
                "activeSessions", activeSessions.size(),
                "serviceType", "webrtc",
                "error", e.getMessage(),
                "timestamp", LocalDateTime.now().toString()
            );
        }
    }

    /**
     * Test OpenAI API connectivity
     */
    private boolean testOpenAIConnectivity() {
        try {
            if (realtimeConfig.getApiKey() == null || realtimeConfig.getApiKey().trim().isEmpty()) {
                return false;
            }

            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + realtimeConfig.getApiKey());

            HttpEntity<String> entity = new HttpEntity<>(headers);

            // Simple API call to test connectivity
            ResponseEntity<Map> response = restTemplate.exchange(
                "https://api.openai.com/v1/models",
                HttpMethod.GET,
                entity,
                Map.class
            );

            return response.getStatusCode().is2xxSuccessful();

        } catch (Exception e) {
            log.debug("OpenAI connectivity test failed", e);
            return false;
        }
    }

    /**
     * Security and validation helper methods
     */

    private void validateTokenRequest(String customerId, String sessionType) {
        if (customerId == null || customerId.trim().isEmpty()) {
            throw new IllegalArgumentException("Customer ID cannot be null or empty");
        }

        if (sessionType == null || sessionType.trim().isEmpty()) {
            throw new IllegalArgumentException("Session type cannot be null or empty");
        }

        // Validate customer ID format (prevent injection attacks)
        if (!customerId.matches("^[a-zA-Z0-9_-]+$")) {
            throw new IllegalArgumentException("Invalid customer ID format");
        }

        // Validate session type
        List<String> allowedSessionTypes = List.of("voice_ordering", "voice_support", "voice_feedback");
        if (!allowedSessionTypes.contains(sessionType)) {
            throw new IllegalArgumentException("Invalid session type: " + sessionType);
        }

        log.debug("Token request validation passed for customer: {}, sessionType: {}", customerId, sessionType);
    }

    private boolean checkRateLimit(String customerId) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime oneHourAgo = now.minusHours(1);

        // Get or create request history for customer
        List<LocalDateTime> requestHistory = tokenRequestHistory.computeIfAbsent(customerId, k ->
            new java.util.ArrayList<>());

        // Remove old requests
        requestHistory.removeIf(timestamp -> timestamp.isBefore(oneHourAgo));

        // Check if under rate limit
        boolean withinLimit = requestHistory.size() < MAX_TOKENS_PER_HOUR;

        if (!withinLimit) {
            log.warn("Rate limit exceeded for customer: {} ({} requests in last hour)",
                customerId, requestHistory.size());
        }

        return withinLimit;
    }

    private void updateRateLimit(String customerId) {
        List<LocalDateTime> requestHistory = tokenRequestHistory.computeIfAbsent(customerId, k ->
            new java.util.ArrayList<>());
        requestHistory.add(LocalDateTime.now());
    }

    private String generateSecureSessionId() {
        byte[] randomBytes = new byte[16];
        secureRandom.nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }

    private String generateRequestId() {
        return "req_" + UUID.randomUUID().toString().replace("-", "");
    }

    private String hashCustomerId(String customerId) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(customerId.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash).substring(0, 16);
        } catch (Exception e) {
            log.warn("Failed to hash customer ID, using truncated version", e);
            return customerId.length() > 8 ? customerId.substring(0, 8) : customerId;
        }
    }

    private String generateRateLimitKey(String customerId) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKeySpec = new SecretKeySpec(
                realtimeConfig.getApiKey().substring(0, 32).getBytes(StandardCharsets.UTF_8),
                "HmacSHA256"
            );
            mac.init(secretKeySpec);
            byte[] hash = mac.doFinal(customerId.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash).substring(0, 12);
        } catch (Exception e) {
            log.warn("Failed to generate rate limit key, using simple hash", e);
            return String.valueOf(customerId.hashCode());
        }
    }

    private void storeTokenValidation(String token, String customerId, String sessionType,
                                    String internalSessionId, String openaiSessionId, long expirationTime) {
        TokenValidation validation = TokenValidation.builder()
            .token(token)
            .customerId(customerId)
            .sessionType(sessionType)
            .internalSessionId(internalSessionId)
            .openaiSessionId(openaiSessionId)
            .createdAt(LocalDateTime.now())
            .expiresAt(LocalDateTime.now().plusSeconds(expirationTime - System.currentTimeMillis() / 1000))
            .isValid(true)
            .build();

        tokenValidationCache.put(token, validation);

        log.debug("Stored token validation for session: {}", internalSessionId);
    }

    /**
     * Validate an existing token
     */
    public boolean validateToken(String token) {
        TokenValidation validation = tokenValidationCache.get(token);

        if (validation == null) {
            log.warn("Token not found in validation cache");
            return false;
        }

        if (!validation.isValid()) {
            log.warn("Token marked as invalid");
            return false;
        }

        if (validation.getExpiresAt().isBefore(LocalDateTime.now())) {
            log.warn("Token has expired");
            validation.setValid(false);
            return false;
        }

        return true;
    }

    /**
     * Clean up expired sessions and tokens
     */
    public void cleanupExpiredSessions() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime cutoff = now.minusHours(2);

        // Clean up expired sessions
        activeSessions.entrySet().removeIf(entry -> {
            WebRTCSession session = entry.getValue();
            boolean expired = session.getCreatedAt().isBefore(cutoff);

            if (expired) {
                log.info("Removing expired WebRTC session: {} for customer: {}",
                    session.getSessionId(), session.getCustomerId());
            }

            return expired;
        });

        // Clean up expired token validations
        tokenValidationCache.entrySet().removeIf(entry -> {
            TokenValidation validation = entry.getValue();
            boolean expired = validation.getExpiresAt().isBefore(now);

            if (expired) {
                log.debug("Removing expired token validation for session: {}",
                    validation.getInternalSessionId());
            }

            return expired;
        });

        // Clean up old rate limiting data
        tokenRequestHistory.entrySet().removeIf(entry -> {
            List<LocalDateTime> history = entry.getValue();
            history.removeIf(timestamp -> timestamp.isBefore(now.minusHours(1)));
            return history.isEmpty();
        });
    }

    // Legacy methods for backward compatibility with existing controller

    /**
     * Handle client connection (legacy compatibility)
     */
    public String handleClientConnection(String clientSessionId, String customerId, String customerEmail) {
        log.info("Legacy connection handler called for client: {}, redirecting to WebRTC", clientSessionId);
        return createWebRTCSession(customerId, "voice_ordering");
    }

    /**
     * Handle client audio (legacy compatibility - no-op for WebRTC)
     */
    public void handleClientAudio(String clientSessionId, byte[] audioData) {
        log.debug("Legacy audio handler called - no-op for WebRTC implementation");
    }

    /**
     * Handle client disconnection (legacy compatibility)
     */
    public void handleClientDisconnection(String clientSessionId) {
        log.info("Legacy disconnection handler called for client: {}", clientSessionId);
    }

    /**
     * WebRTC Session data class
     */
    @lombok.Builder
    @lombok.Data
    public static class WebRTCSession {
        private String sessionId;
        private String customerId;
        private String sessionType;
        private LocalDateTime createdAt;
        private LocalDateTime lastActivity;
        private String status;
        private String connectionStatus;
        private OrderContext orderContext;
        private Map<String, Object> voiceMetrics;
    }

    /**
     * Token Validation data class
     */
    @lombok.Builder
    @lombok.Data
    public static class TokenValidation {
        private String token;
        private String customerId;
        private String sessionType;
        private String internalSessionId;
        private String openaiSessionId;
        private LocalDateTime createdAt;
        private LocalDateTime expiresAt;
        private boolean isValid;
    }

    /**
     * Order Context data class for session-based order management
     */
    @lombok.Builder
    @lombok.Data
    public static class OrderContext {
        private String customerId;
        private String sessionId;
        private List<Map<String, Object>> items;
        private Double subtotal;
        private Double tax;
        private Double total;
        private String status;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }
}