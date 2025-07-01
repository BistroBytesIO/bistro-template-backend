package com.bistro_template_backend.controllers;

import com.bistro_template_backend.services.WebRTCVoiceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.annotation.SubscribeMapping;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.time.LocalDateTime;

@Controller
@RequiredArgsConstructor
@Slf4j
public class RealtimeVoiceController {

    private final WebRTCVoiceService webRTCVoiceService;

    /**
     * Handle client connection to realtime voice
     */
    @SubscribeMapping("/voice/realtime/connect")
    public void handleConnection(
            SimpMessageHeaderAccessor headerAccessor,
            @RequestParam(defaultValue = "guest") String customerId,
            @RequestParam(required = false) String customerEmail
    ) {
        String clientSessionId = headerAccessor.getSessionId();

        log.info("Client connecting to realtime voice: {}", clientSessionId);

        String sessionId = webRTCVoiceService.handleClientConnection(
                clientSessionId, customerId, customerEmail
        );

        log.info("Realtime voice session created: {}", sessionId);
    }

    /**
     * Handle audio data from client
     */
    @MessageMapping("/voice/realtime/audio")
    public void handleAudioData(
            @Payload byte[] audioData,
            SimpMessageHeaderAccessor headerAccessor
    ) {
        String clientSessionId = headerAccessor.getSessionId();

        log.debug("Received audio data from client: {} bytes", audioData.length);

        webRTCVoiceService.handleClientAudio(clientSessionId, audioData);
    }

    /**
     * Handle client disconnection
     */
    @MessageMapping("/voice/realtime/disconnect")
    public void handleDisconnection(SimpMessageHeaderAccessor headerAccessor) {
        String clientSessionId = headerAccessor.getSessionId();

        log.info("Client disconnecting from realtime voice: {}", clientSessionId);

        webRTCVoiceService.handleClientDisconnection(clientSessionId);
    }

    /**
     * Generate ephemeral token for WebRTC direct connection to OpenAI
     */
    @PostMapping("/api/voice/webrtc/token")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> generateWebRTCToken(@RequestBody Map<String, String> request) {
        try {
            String customerId = request.getOrDefault("customerId", "guest");
            String sessionType = request.getOrDefault("sessionType", "voice_ordering");
            
            log.info("Generating WebRTC token for customer: {}, sessionType: {}", customerId, sessionType);
            
            // Generate ephemeral token for direct OpenAI access
            String token = webRTCVoiceService.generateEphemeralToken(customerId, sessionType);
            
            // Create session for tracking
            String sessionId = webRTCVoiceService.createWebRTCSession(customerId, sessionType);
            
            return ResponseEntity.ok(Map.of(
                "token", token,
                "sessionId", sessionId,
                "expiresIn", 3600, // 1 hour
                "type", "webrtc_ephemeral"
            ));
            
        } catch (Exception e) {
            log.error("Failed to generate WebRTC token", e);
            return ResponseEntity.status(500).body(Map.of(
                    "error", "Token generation failed",
                    "message", e.getMessage()
            ));
        }
    }

    /**
     * WebRTC session management endpoint
     */
    @GetMapping("/api/voice/webrtc/session/{sessionId}")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getWebRTCSession(@PathVariable String sessionId) {
        try {
            Map<String, Object> session = webRTCVoiceService.getWebRTCSessionStatus(sessionId);
            
            if (session.isEmpty()) {
                return ResponseEntity.notFound().build();
            }
            
            return ResponseEntity.ok(session);
            
        } catch (Exception e) {
            log.error("Failed to get WebRTC session status", e);
            return ResponseEntity.status(500).body(Map.of(
                    "error", "Session status failed",
                    "message", e.getMessage()
            ));
        }
    }

    /**
     * Validate WebRTC session
     */
    @GetMapping("/api/voice/webrtc/session/{sessionId}/validate")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> validateSession(@PathVariable String sessionId) {
        try {
            boolean isValid = webRTCVoiceService.validateSession(sessionId);
            
            return ResponseEntity.ok(Map.of(
                "sessionId", sessionId,
                "valid", isValid,
                "timestamp", LocalDateTime.now().toString()
            ));
            
        } catch (Exception e) {
            log.error("Failed to validate session", e);
            return ResponseEntity.status(500).body(Map.of(
                    "error", "Session validation failed",
                    "message", e.getMessage()
            ));
        }
    }

    /**
     * Get order summary for WebRTC session
     */
    @GetMapping("/api/voice/webrtc/session/{sessionId}/order")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getSessionOrder(@PathVariable String sessionId) {
        try {
            Map<String, Object> orderSummary = webRTCVoiceService.getOrderSummary(sessionId);
            
            if (orderSummary.containsKey("error")) {
                return ResponseEntity.notFound().build();
            }
            
            return ResponseEntity.ok(orderSummary);
            
        } catch (Exception e) {
            log.error("Failed to get session order", e);
            return ResponseEntity.status(500).body(Map.of(
                    "error", "Failed to get order summary",
                    "message", e.getMessage()
            ));
        }
    }

    /**
     * Update session connection status
     */
    @PostMapping("/api/voice/webrtc/session/{sessionId}/connection")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> updateConnectionStatus(
            @PathVariable String sessionId, 
            @RequestBody Map<String, String> request) {
        try {
            String connectionStatus = request.get("connectionStatus");
            
            if (connectionStatus == null || connectionStatus.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of(
                    "error", "Connection status is required"
                ));
            }
            
            webRTCVoiceService.updateSessionConnectionStatus(sessionId, connectionStatus);
            
            return ResponseEntity.ok(Map.of(
                "sessionId", sessionId,
                "connectionStatus", connectionStatus,
                "updated", true,
                "timestamp", LocalDateTime.now().toString()
            ));
            
        } catch (Exception e) {
            log.error("Failed to update connection status", e);
            return ResponseEntity.status(500).body(Map.of(
                    "error", "Failed to update connection status",
                    "message", e.getMessage()
            ));
        }
    }

    /**
     * Health check endpoint for realtime voice service
     */
    @GetMapping("/api/voice/realtime/health")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> healthCheck() {
        try {
            Map<String, Object> status = webRTCVoiceService.getConnectionStatus();

            boolean healthy = (boolean) status.getOrDefault("healthy", false);

            return healthy ?
                    ResponseEntity.ok(status) :
                    ResponseEntity.status(503).body(status);

        } catch (Exception e) {
            log.error("Health check failed", e);
            return ResponseEntity.status(500).body(Map.of(
                    "error", "Health check failed",
                    "message", e.getMessage()
            ));
        }
    }
}