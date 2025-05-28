package com.bistro_template_backend.controllers;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/websocket")
@CrossOrigin(origins = {"http://localhost:3000", "http://localhost:5173", "http://127.0.0.1:5173"}, allowCredentials = "false")
public class WebSocketStatusController {

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @GetMapping("/status")
    public Map<String, Object> getWebSocketStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("websocketEnabled", true);
        status.put("timestamp", LocalDateTime.now());
        status.put("endpoints", new String[]{
                "/ws-orders (SockJS)",
                "/ws-orders (Native WebSocket)"
        });
        System.out.println("✅ WebSocket status check - WebSocket is enabled and running");
        return status;
    }

    @PostMapping("/test")
    public Map<String, Object> testWebSocketMessage() {
        try {
            Map<String, Object> testMessage = new HashMap<>();
            testMessage.put("notificationType", "TEST_MESSAGE");
            testMessage.put("message", "🧪 WebSocket test message sent at " + LocalDateTime.now());
            testMessage.put("orderId", 999);
            testMessage.put("customerName", "Test Customer");
            testMessage.put("timestamp", System.currentTimeMillis());

            // Send the test message
            messagingTemplate.convertAndSend("/topic/orders", testMessage);

            System.out.println("🧪 Test WebSocket message sent: " + testMessage);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Test message sent successfully to /topic/orders");
            response.put("sentMessage", testMessage);

            return response;
        } catch (Exception e) {
            System.err.println("❌ Error sending test message: " + e.getMessage());
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("error", e.getMessage());
            return response;
        }
    }

    @GetMapping("/info")
    public Map<String, Object> getConnectionInfo() {
        Map<String, Object> info = new HashMap<>();
        info.put("websocketUrl", "ws://localhost:8080/ws-orders/websocket");
        info.put("sockjsUrl", "http://localhost:8080/ws-orders");
        info.put("topic", "/topic/orders");
        info.put("testEndpoint", "/api/websocket/test");
        return info;
    }
}