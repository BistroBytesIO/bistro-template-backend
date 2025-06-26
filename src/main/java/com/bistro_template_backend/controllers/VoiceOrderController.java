package com.bistro_template_backend.controllers;

import com.bistro_template_backend.services.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api/voice")
@RequiredArgsConstructor
@Slf4j
public class VoiceOrderController {

    private final VoiceAIService voiceAIService;
    private final VoiceSessionManager voiceSessionManager;
    @Lazy private final ConversationContextService conversationContextService;
    private final AudioProcessingService audioProcessingService;
    @Lazy private final VoiceOrderService voiceOrderService;

    /**
     * Start a new voice ordering session
     */
    @PostMapping("/session/start")
    public ResponseEntity<?> startVoiceSession(@RequestBody Map<String, String> request) {
        try {
            String customerId = request.get("customerId");
            String customerEmail = request.get("customerEmail");
            
            if (customerId == null || customerId.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(
                    Map.of("error", "Customer ID is required to start voice session")
                );
            }

            String sessionId = voiceSessionManager.createSession(customerId, customerEmail);
            
            Map<String, Object> response = new HashMap<>();
            response.put("sessionId", sessionId);
            response.put("message", "Voice session started successfully");
            response.put("menuContext", conversationContextService.buildMenuContext());
            response.put("instructions", "You can now start placing your order by voice. " +
                    "Say something like 'I'd like to order a burger' to begin.");

            log.info("Started voice session: {} for customer: {}", sessionId, customerId);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error starting voice session", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to start voice session: " + e.getMessage()));
        }
    }

    /**
     * Process voice input (speech-to-text + conversation + text-to-speech)
     */
    @PostMapping(value = "/session/{sessionId}/process", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> processVoiceInput(
            @PathVariable String sessionId,
            @RequestParam("audio") MultipartFile audioFile,
            @RequestParam(value = "language", defaultValue = "en") String language) {
        
        try {
            log.info("Processing voice input for session: {}", sessionId);

            // Validate session exists
            VoiceSessionManager.VoiceSession session = voiceSessionManager.getSession(sessionId);
            
            // Validate audio file
            audioProcessingService.validateAudioFile(audioFile);

            // Get conversation context
            List<String> conversationHistory = session.getConversationHistory();
            String menuContext = conversationContextService.buildFocusedMenuContext(
                    "", sessionId); // We'll get the actual message after speech-to-text
            String orderContext = conversationContextService.getOrderSummary(sessionId);

            // Process voice interaction asynchronously
            CompletableFuture<VoiceAIService.VoiceProcessingResult> resultFuture = 
                    voiceAIService.processVoiceInteraction(
                            audioFile, sessionId, language, conversationHistory, menuContext, orderContext);

            // Wait for result (with timeout)
            VoiceAIService.VoiceProcessingResult result = resultFuture.get();

            if (!result.isSuccess()) {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(Map.of("error", "Voice processing failed: " + result.getError()));
            }

            // Process order intent from transcription
            ConversationContextService.OrderUpdateResult orderResult = 
                    conversationContextService.processOrderIntent(result.getTranscription(), sessionId);

            // Add conversation turn to session
            voiceSessionManager.addConversationTurn(sessionId, result.getTranscription(), result.getAiResponse());

            // Prepare response
            Map<String, Object> response = new HashMap<>();
            response.put("sessionId", sessionId);
            response.put("transcription", result.getTranscription());
            response.put("aiResponse", result.getAiResponse());
            response.put("orderUpdated", orderResult.isOrderUpdated());
            
            if (orderResult.isOrderUpdated()) {
                response.put("orderSummary", conversationContextService.getOrderSummary(sessionId));
                response.put("orderAction", orderResult.getAction());
            }

            log.info("Voice input processed successfully for session: {}", sessionId);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error processing voice input for session: {}", sessionId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to process voice input: " + e.getMessage()));
        }
    }

    /**
     * Get audio response for text-to-speech
     */
    @PostMapping("/session/{sessionId}/tts")
    public ResponseEntity<?> getAudioResponse(@PathVariable String sessionId, 
                                            @RequestBody Map<String, String> request) {
        try {
            String text = request.get("text");
            if (text == null || text.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(
                    Map.of("error", "Text is required for text-to-speech conversion")
                );
            }

            // Validate session
            voiceSessionManager.getSession(sessionId);

            byte[] audioBytes = voiceAIService.textToSpeech(text);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType("audio/mpeg"));
            headers.setContentLength(audioBytes.length);
            headers.set("Cache-Control", "public, max-age=3600");

            log.info("Generated TTS audio for session: {} ({})", sessionId, text.length() + " characters");
            return new ResponseEntity<>(audioBytes, headers, HttpStatus.OK);

        } catch (Exception e) {
            log.error("Error generating TTS audio for session: {}", sessionId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to generate audio: " + e.getMessage()));
        }
    }

    /**
     * Get current order summary
     */
    @GetMapping("/session/{sessionId}/order")
    public ResponseEntity<?> getCurrentOrder(@PathVariable String sessionId) {
        try {
            VoiceSessionManager.VoiceSession session = voiceSessionManager.getSession(sessionId);
            String orderSummary = conversationContextService.getOrderSummary(sessionId);
            
            Map<String, Object> response = new HashMap<>();
            response.put("sessionId", sessionId);
            response.put("orderSummary", orderSummary);
            response.put("currentOrder", session.getCurrentOrder());
            response.put("turnCount", session.getTurnCount());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error getting current order for session: {}", sessionId, e);
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Session not found or expired"));
        }
    }

    /**
     * Finalize voice order and convert to regular order
     */
    @PostMapping("/session/{sessionId}/finalize")
    public ResponseEntity<?> finalizeVoiceOrder(@PathVariable String sessionId,
                                               @RequestBody Map<String, String> request) {
        try {
            VoiceSessionManager.VoiceSession session = voiceSessionManager.getSession(sessionId);
            
            // Validate order has items
            if (session.getCurrentOrder().getItems().isEmpty()) {
                return ResponseEntity.badRequest().body(
                    Map.of("error", "Cannot finalize empty order")
                );
            }

            // Create regular order from voice order
            Long orderId = voiceOrderService.createOrderFromVoiceSession(sessionId, request);
            
            // Close voice session
            voiceSessionManager.closeSession(sessionId, "Order finalized");

            Map<String, Object> response = new HashMap<>();
            response.put("orderId", orderId);
            response.put("message", "Voice order successfully finalized");
            response.put("sessionId", sessionId);

            log.info("Finalized voice order - Session: {}, Order ID: {}", sessionId, orderId);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error finalizing voice order for session: {}", sessionId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to finalize order: " + e.getMessage()));
        }
    }

    /**
     * Cancel voice session
     */
    @PostMapping("/session/{sessionId}/cancel")
    public ResponseEntity<?> cancelVoiceSession(@PathVariable String sessionId) {
        try {
            voiceSessionManager.closeSession(sessionId, "Cancelled by user");
            
            Map<String, Object> response = new HashMap<>();
            response.put("message", "Voice session cancelled successfully");
            response.put("sessionId", sessionId);

            log.info("Cancelled voice session: {}", sessionId);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error cancelling voice session: {}", sessionId, e);
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Session not found"));
        }
    }

    /**
     * Get voice session status and statistics
     */
    @GetMapping("/session/{sessionId}/status")
    public ResponseEntity<?> getSessionStatus(@PathVariable String sessionId) {
        try {
            VoiceSessionManager.VoiceSession session = voiceSessionManager.getSession(sessionId);
            
            Map<String, Object> response = new HashMap<>();
            response.put("sessionId", sessionId);
            response.put("active", session.isActive());
            response.put("turnCount", session.getTurnCount());
            response.put("createdAt", session.getCreatedAt());
            response.put("lastActivityAt", session.getLastActivityAt());
            response.put("itemsInOrder", session.getCurrentOrder().getItems().size());
            response.put("orderStatus", session.getCurrentOrder().getStatus());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Session not found or expired"));
        }
    }

    /**
     * Get rate limit status for the API
     */
    @GetMapping("/rate-limit-status")
    public ResponseEntity<?> getRateLimitStatus() {
        try {
            Map<String, Object> rateLimitStatus = voiceAIService.getRateLimitStatus();
            Map<String, Object> tempDirStats = audioProcessingService.getTempDirectoryStats();
            
            Map<String, Object> response = new HashMap<>();
            response.put("rateLimits", rateLimitStatus);
            response.put("tempDirectory", tempDirStats);
            response.put("activeSessionCount", voiceSessionManager.getActiveSessions().size());
            response.putAll(voiceSessionManager.getSessionStatistics());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error getting rate limit status", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to get status"));
        }
    }

    /**
     * Health check endpoint for voice AI services
     */
    @GetMapping("/health")
    public ResponseEntity<?> healthCheck() {
        try {
            Map<String, Object> health = new HashMap<>();
            health.put("status", "UP");
            health.put("voiceAI", "Available");
            health.put("activeSessionCount", voiceSessionManager.getActiveSessions().size());
            health.put("timestamp", System.currentTimeMillis());

            // Test basic functionality
            Map<String, Object> rateLimits = voiceAIService.getRateLimitStatus();
            health.put("rateLimitsAvailable", rateLimits);

            return ResponseEntity.ok(health);

        } catch (Exception e) {
            log.error("Voice AI health check failed", e);
            
            Map<String, Object> health = new HashMap<>();
            health.put("status", "DOWN");
            health.put("error", e.getMessage());
            health.put("timestamp", System.currentTimeMillis());

            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(health);
        }
    }

    /**
     * Manual cleanup endpoint for testing
     */
    @PostMapping("/admin/cleanup")
    public ResponseEntity<?> manualCleanup() {
        try {
            voiceAIService.cleanupTempFiles();
            audioProcessingService.cleanupOldTempFiles();
            
            Map<String, Object> response = new HashMap<>();
            response.put("message", "Cleanup completed successfully");
            response.put("timestamp", System.currentTimeMillis());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error during manual cleanup", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Cleanup failed: " + e.getMessage()));
        }
    }
}