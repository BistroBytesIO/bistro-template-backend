package com.bistro_template_backend.services;

import com.bistro_template_backend.config.RealtimeConfig;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.*;
import org.springframework.web.socket.client.WebSocketConnectionManager;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.net.URI;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

@Service
@Slf4j
@RequiredArgsConstructor
public class RealtimeVoiceService implements WebSocketHandler {

    private final RealtimeConfig realtimeConfig;
    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper;

    // WebSocket connection management
    private WebSocketSession openaiSession;
    private WebSocketConnectionManager connectionManager;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    private final AtomicBoolean isConnecting = new AtomicBoolean(false);
    private final AtomicInteger reconnectAttempts = new AtomicInteger(0);
    private final int maxReconnectAttempts = 5;

    // Client session management
    private final Map<String, ClientSessionData> clientSessions = new ConcurrentHashMap<>();

    @PostConstruct
    public void initialize() {
        // Delay initial connection to ensure all beans are ready
        scheduler.schedule(this::connectToOpenAI, 2, TimeUnit.SECONDS);
        startHeartbeat();
    }

    @PreDestroy
    public void cleanup() {
        log.info("Cleaning up RealtimeVoiceService...");
        scheduler.shutdown();

        if (connectionManager != null) {
            try {
                connectionManager.stop();
            } catch (Exception e) {
                log.warn("Error stopping connection manager", e);
            }
        }

        if (openaiSession != null && openaiSession.isOpen()) {
            try {
                openaiSession.close();
            } catch (IOException e) {
                log.warn("Error closing OpenAI session", e);
            }
        }
    }

    private void connectToOpenAI() {
        if (isConnecting.get()) {
            log.debug("Connection attempt already in progress, skipping");
            return;
        }

        if (reconnectAttempts.get() >= maxReconnectAttempts) {
            log.error("Max reconnection attempts reached ({}), giving up", maxReconnectAttempts);
            return;
        }

        isConnecting.set(true);

        try {
            log.info("Connecting to OpenAI Realtime API... (attempt {})", reconnectAttempts.get() + 1);

            // Validate API key
            if (realtimeConfig.getApiKey() == null || realtimeConfig.getApiKey().trim().isEmpty()) {
                log.error("OpenAI API key is not configured");
                isConnecting.set(false);
                return;
            }

            // Clean up previous connection manager
            if (connectionManager != null) {
                try {
                    connectionManager.stop();
                } catch (Exception e) {
                    log.debug("Error stopping previous connection manager", e);
                }
            }

            String wsUrl = realtimeConfig.getWebSocketUrl();
            log.info("Connecting to WebSocket URL: {}", wsUrl);

            connectionManager = new WebSocketConnectionManager(
                    new StandardWebSocketClient(),
                    this,
                    wsUrl
            );

            // Set authentication headers
            connectionManager.setHeaders(createAuthHeaders());

            // Configure connection timeouts
            connectionManager.setAutoStartup(false);

            connectionManager.start();

        } catch (Exception e) {
            log.error("Failed to connect to OpenAI Realtime API", e);
            isConnecting.set(false);
            scheduleReconnection();
        }
    }

    private WebSocketHttpHeaders createAuthHeaders() {
        WebSocketHttpHeaders headers = new WebSocketHttpHeaders();
        headers.add("Authorization", "Bearer " + realtimeConfig.getApiKey());
        headers.add("OpenAI-Beta", "realtime=v1");
        headers.add("User-Agent", "Bistro-Voice-Service/1.0");
        return headers;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        this.openaiSession = session;
        isConnecting.set(false);
        reconnectAttempts.set(0); // Reset on successful connection

        log.info("✅ Connected to OpenAI Realtime API - Session ID: {}", session.getId());
        log.info("Session URI: {}", session.getUri());
        log.info("Session attributes: {}", session.getAttributes());

        // Configure session with restaurant context
        configureOpenAISession();
    }

    @Override
    public void handleMessage(WebSocketSession session, WebSocketMessage<?> message) {
        try {
            // FIXED: Better message type detection and handling
            String payload = null;

            if (message instanceof TextMessage) {
                payload = ((TextMessage) message).getPayload();
            } else if (message instanceof BinaryMessage) {
                BinaryMessage binaryMessage = (BinaryMessage) message;
                payload = new String(binaryMessage.getPayload().array());
            } else {
                log.warn("Received unsupported message type: {}", message.getClass().getSimpleName());
                return;
            }

            // Log first part of message for debugging
            log.debug("Received OpenAI message ({}): {}",
                    message.getClass().getSimpleName(),
                    payload.substring(0, Math.min(payload.length(), 200))
            );

            // FIXED: Validate JSON before parsing
            if (!isValidJson(payload)) {
                log.warn("Received non-JSON message, skipping: {}",
                        payload.substring(0, Math.min(payload.length(), 100)));
                return;
            }

            // Parse as JSON
            JsonNode eventNode = objectMapper.readTree(payload);
            handleOpenAIEvent(eventNode);

        } catch (com.fasterxml.jackson.core.JsonParseException e) {
            log.error("JSON parsing error for message: {}",
                    message.getPayload().toString().substring(0, Math.min(message.getPayload().toString().length(), 100)), e);
            // Don't reconnect on JSON parsing errors - log and continue
        } catch (Exception e) {
            log.error("Unexpected error handling OpenAI message: {}", e.getMessage(), e);
            // For other unexpected errors, we might need to reconnect
            if (e instanceof IOException) {
                scheduleReconnection();
            }
        }
    }

    /**
     * Check if a string is valid JSON
     */
    private boolean isValidJson(String jsonString) {
        if (jsonString == null || jsonString.trim().isEmpty()) {
            return false;
        }

        // Quick check for JSON start characters
        String trimmed = jsonString.trim();
        if (!trimmed.startsWith("{") && !trimmed.startsWith("[")) {
            return false;
        }

        try {
            objectMapper.readTree(jsonString);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.error("OpenAI WebSocket transport error: {}", exception.getMessage());
        log.debug("Full transport error:", exception);
        isConnecting.set(false);

        // Check if it's a connection error vs other errors
        if (exception instanceof IOException ||
                exception.getMessage().contains("Connection refused") ||
                exception.getMessage().contains("Connection reset")) {
            scheduleReconnection();
        } else {
            log.error("Non-recoverable transport error, not reconnecting: {}", exception.getMessage());
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus closeStatus) {
        log.warn("OpenAI WebSocket connection closed: {} - {}", closeStatus.getCode(), closeStatus.getReason());
        this.openaiSession = null;
        isConnecting.set(false);

        // Analyze close status
        switch (closeStatus.getCode()) {
            case 1000: // Normal closure
                log.info("Connection closed normally");
                break;
            case 1001: // Going away
                log.info("Connection closed - going away");
                break;
            case 1006: // Abnormal closure
                log.warn("Connection closed abnormally");
                scheduleReconnection();
                break;
            case 1011: // Server error
                log.error("Server error caused connection close");
                scheduleReconnection();
                break;
            case 4000: // OpenAI specific error codes
            case 4001:
            case 4002:
                log.error("OpenAI API error ({}): {}", closeStatus.getCode(), closeStatus.getReason());
                // Don't reconnect immediately for API errors
                break;
            default:
                log.warn("Unexpected close code: {}", closeStatus.getCode());
                scheduleReconnection();
        }
    }

    @Override
    public boolean supportsPartialMessages() {
        return false;
    }

    private void configureOpenAISession() {
        try {
            log.info("Configuring OpenAI session with restaurant context...");

            // Build session configuration
            Map<String, Object> sessionUpdate = Map.of(
                    "type", "session.update",
                    "session", Map.of(
                            "modalities", List.of("text", "audio"),
                            "instructions", buildRestaurantInstructions(),
                            "voice", realtimeConfig.getVoice(),
                            "input_audio_format", realtimeConfig.getAudioFormat(),
                            "output_audio_format", realtimeConfig.getAudioFormat(),
                            "input_audio_transcription", Map.of(
                                    "model", realtimeConfig.getTranscriptionModel()
                            ),
                            "turn_detection", Map.of(
                                    "type", realtimeConfig.getVadType(),
                                    "threshold", 0.5,
                                    "prefix_padding_ms", 300,
                                    "silence_duration_ms", 500
                            ),
                            "tools", buildOrderManagementTools()
                    )
            );

            sendToOpenAI(sessionUpdate);
            log.info("✅ Session configured with restaurant context");

        } catch (Exception e) {
            log.error("❌ Failed to configure OpenAI session", e);
        }
    }

    private String buildRestaurantInstructions() {
        return """
            You are a helpful voice assistant for a restaurant ordering system.
            Help customers order food by understanding their preferences and guiding them through the menu.
            
            Guidelines:
            - Be friendly and conversational
            - Ask clarifying questions when needed
            - Confirm orders before finalizing
            - Help with menu recommendations
            - Handle order modifications naturally
            
            When customers want to add items, use the add_item_to_order function.
            When they want to remove items, use the remove_item_from_order function.
            When they're ready to checkout, use the finalize_order function.
            
            Keep responses natural and engaging. You're here to make ordering easy and enjoyable!
            """;
    }

    private List<Map<String, Object>> buildOrderManagementTools() {
        return List.of(
                Map.of(
                        "type", "function",
                        "name", "add_item_to_order",
                        "description", "Add an item to the customer's order",
                        "parameters", Map.of(
                                "type", "object",
                                "properties", Map.of(
                                        "item_name", Map.of("type", "string", "description", "Name of the menu item"),
                                        "quantity", Map.of("type", "integer", "description", "Quantity to add"),
                                        "customizations", Map.of("type", "array", "items", Map.of("type", "string"), "description", "Any customizations"),
                                        "notes", Map.of("type", "string", "description", "Special notes for this item")
                                ),
                                "required", List.of("item_name", "quantity")
                        )
                ),
                Map.of(
                        "type", "function",
                        "name", "get_order_summary",
                        "description", "Get the current order summary and total",
                        "parameters", Map.of("type", "object", "properties", Map.of())
                )
        );
    }

    /**
     * Handle client connection from frontend
     */
    public String handleClientConnection(String clientSessionId, String customerId, String customerEmail) {
        String sessionId = "client_" + System.currentTimeMillis();

        ClientSessionData sessionData = new ClientSessionData();
        sessionData.setSessionId(sessionId);
        sessionData.setClientSessionId(clientSessionId);
        sessionData.setCustomerId(customerId);
        sessionData.setCustomerEmail(customerEmail);
        sessionData.setConnectedAt(System.currentTimeMillis());

        clientSessions.put(clientSessionId, sessionData);

        log.info("Client connected to realtime voice session: {}", sessionId);

        // Check if OpenAI connection is available
        boolean openaiConnected = (openaiSession != null && openaiSession.isOpen());

        // Notify client of connection status
        messagingTemplate.convertAndSendToUser(
                clientSessionId,
                "/topic/voice/realtime/status",
                Map.of(
                        "status", openaiConnected ? "connected" : "connecting",
                        "sessionId", sessionId,
                        "openaiConnected", openaiConnected
                )
        );

        return sessionId;
    }

    /**
     * Handle audio data from client
     */
    public void handleClientAudio(String clientSessionId, byte[] audioData) {
        ClientSessionData sessionData = clientSessions.get(clientSessionId);
        if (sessionData == null) {
            log.warn("No session data found for client: {}", clientSessionId);
            return;
        }

        if (openaiSession == null || !openaiSession.isOpen()) {
            log.warn("OpenAI session not available for audio processing");
            // Notify client that backend is not ready
            messagingTemplate.convertAndSendToUser(
                    clientSessionId,
                    "/topic/voice/realtime/error",
                    Map.of("error", "Backend connection not available")
            );
            return;
        }

        try {
            // Create audio buffer append event
            Map<String, Object> event = Map.of(
                    "type", "input_audio_buffer.append",
                    "audio", Base64.getEncoder().encodeToString(audioData)
            );

            sendToOpenAI(event);
            sessionData.setLastActivityAt(System.currentTimeMillis());

        } catch (Exception e) {
            log.error("Failed to process client audio", e);
        }
    }

    /**
     * Handle events from OpenAI Realtime API with JsonNode
     */
    private void handleOpenAIEvent(JsonNode eventNode) {
        try {
            String eventType = eventNode.get("type").asText();
            log.debug("Processing OpenAI event: {}", eventType);

            switch (eventType) {
                case "session.created":
                    handleSessionCreated(eventNode);
                    break;
                case "session.updated":
                    handleSessionUpdated(eventNode);
                    break;
                case "response.audio.delta":
                    handleAudioDelta(eventNode);
                    break;
                case "response.audio_transcript.delta":
                    handleTranscriptDelta(eventNode);
                    break;
                case "conversation.item.created":
                    handleConversationItemCreated(eventNode);
                    break;
                case "input_audio_buffer.speech_started":
                    handleSpeechStarted(eventNode);
                    break;
                case "input_audio_buffer.speech_stopped":
                    handleSpeechStopped(eventNode);
                    break;
                case "error":
                    handleError(eventNode);
                    break;
                default:
                    log.debug("Unhandled event type: {}", eventType);
            }

        } catch (Exception e) {
            log.error("Error processing OpenAI event", e);
        }
    }

    private void handleSessionCreated(JsonNode event) {
        log.info("✅ OpenAI session created successfully");
        JsonNode sessionNode = event.get("session");
        if (sessionNode != null) {
            log.debug("Session details: {}", sessionNode);
        }

        // Notify all clients that backend is ready
        broadcastToAllClients("/topic/voice/realtime/status", Map.of(
                "status", "ready",
                "message", "Voice AI backend is ready"
        ));
    }

    private void handleSessionUpdated(JsonNode event) {
        log.info("✅ OpenAI session updated successfully");
    }

    private void handleSpeechStarted(JsonNode event) {
        log.debug("🎤 Speech started detected");
        broadcastToAllClients("/topic/voice/realtime/speech", Map.of(
                "type", "speech_started"
        ));
    }

    private void handleSpeechStopped(JsonNode event) {
        log.debug("🔇 Speech stopped detected");
        broadcastToAllClients("/topic/voice/realtime/speech", Map.of(
                "type", "speech_stopped"
        ));
    }

    private void handleAudioDelta(JsonNode event) {
        JsonNode deltaNode = event.get("delta");
        if (deltaNode != null && !deltaNode.isNull()) {
            String audioDelta = deltaNode.asText();
            // Broadcast audio to all connected clients
            broadcastToAllClients("/topic/voice/realtime/audio", Map.of(
                    "type", "audio_delta",
                    "audio", audioDelta
            ));
        }
    }

    private void handleTranscriptDelta(JsonNode event) {
        JsonNode deltaNode = event.get("delta");
        if (deltaNode != null && !deltaNode.isNull()) {
            String transcriptDelta = deltaNode.asText();
            broadcastToAllClients("/topic/voice/realtime/transcript", Map.of(
                    "type", "transcript_delta",
                    "text", transcriptDelta
            ));
        }
    }

    private void handleConversationItemCreated(JsonNode event) {
        // Convert JsonNode to Map for broadcasting
        try {
            Map<String, Object> eventMap = objectMapper.convertValue(event, new TypeReference<Map<String, Object>>() {});
            broadcastToAllClients("/topic/voice/realtime/conversation", eventMap);
        } catch (Exception e) {
            log.error("Error converting conversation event", e);
        }
    }

    private void handleError(JsonNode event) {
        JsonNode errorNode = event.get("error");
        String errorMessage = "Unknown error";
        String errorType = "unknown";

        if (errorNode != null) {
            errorType = errorNode.path("type").asText("unknown");
            errorMessage = errorNode.path("message").asText("Unknown error");
        }

        log.error("OpenAI API error - Type: {}, Message: {}", errorType, errorMessage);

        try {
            Map<String, Object> eventMap = objectMapper.convertValue(event, new TypeReference<Map<String, Object>>() {});
            broadcastToAllClients("/topic/voice/realtime/error", eventMap);
        } catch (Exception e) {
            log.error("Error converting error event", e);
        }
    }

    private void sendToOpenAI(Object event) {
        if (openaiSession != null && openaiSession.isOpen()) {
            try {
                String json = objectMapper.writeValueAsString(event);
                openaiSession.sendMessage(new TextMessage(json));
                log.debug("📤 Sent to OpenAI: {}", json.substring(0, Math.min(json.length(), 100)));
            } catch (Exception e) {
                log.error("Error sending to OpenAI", e);
            }
        } else {
            log.warn("Cannot send to OpenAI - session not available");
        }
    }

    private void broadcastToAllClients(String destination, Object message) {
        if (clientSessions.isEmpty()) {
            log.debug("No clients connected, skipping broadcast to {}", destination);
            return;
        }

        clientSessions.keySet().forEach(clientSessionId -> {
            try {
                messagingTemplate.convertAndSendToUser(clientSessionId, destination, message);
            } catch (Exception e) {
                log.warn("Failed to send to client: {}", clientSessionId, e);
            }
        });
    }

    private void scheduleReconnection() {
        if (reconnectAttempts.get() >= maxReconnectAttempts) {
            log.error("Max reconnection attempts reached, giving up");
            // Notify clients that backend is unavailable
            broadcastToAllClients("/topic/voice/realtime/error", Map.of(
                    "error", "Backend connection failed",
                    "message", "Voice AI backend is temporarily unavailable"
            ));
            return;
        }

        int attempt = reconnectAttempts.incrementAndGet();
        long delay = (long) Math.min(30, Math.pow(2, attempt)); // Exponential backoff, max 30 seconds

        log.info("Scheduling reconnection attempt {} in {} seconds", attempt, delay);

        scheduler.schedule(() -> {
            log.info("Attempting to reconnect to OpenAI...");
            connectToOpenAI();
        }, delay, TimeUnit.SECONDS);
    }

    private void startHeartbeat() {
        scheduler.scheduleWithFixedDelay(() -> {
            if (openaiSession != null && openaiSession.isOpen()) {
                try {
                    openaiSession.sendMessage(new PingMessage());
                    log.debug("💓 Sent heartbeat ping");
                } catch (Exception e) {
                    log.warn("Heartbeat failed", e);
                }
            }
        }, 60, 30, TimeUnit.SECONDS); // Start after 1 minute, then every 30 seconds
    }

    public void handleClientDisconnection(String clientSessionId) {
        ClientSessionData sessionData = clientSessions.remove(clientSessionId);
        if (sessionData != null) {
            log.info("Client disconnected: {}", sessionData.getSessionId());
        }
    }

    /**
     * Get connection status for health checks
     */
    public Map<String, Object> getConnectionStatus() {
        return Map.of(
                "openaiConnected", openaiSession != null && openaiSession.isOpen(),
                "isConnecting", isConnecting.get(),
                "reconnectAttempts", reconnectAttempts.get(),
                "activeClients", clientSessions.size(),
                "lastConnectionTime", openaiSession != null ? System.currentTimeMillis() : 0
        );
    }

    // Inner class for client session data
    @lombok.Data
    public static class ClientSessionData {
        private String sessionId;
        private String clientSessionId;
        private String customerId;
        private String customerEmail;
        private long connectedAt;
        private long lastActivityAt;
        private Map<String, Object> orderData = new ConcurrentHashMap<>();
    }
}