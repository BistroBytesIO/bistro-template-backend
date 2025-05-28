package com.bistro_template_backend.controllers;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/test")
public class WebSocketTestController {

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @PostMapping("/websocket")
    public String testWebSocket() {
        try {
            Map<String, Object> testMessage = new HashMap<>();
            testMessage.put("message", "WebSocket test message");
            testMessage.put("timestamp", System.currentTimeMillis());

            messagingTemplate.convertAndSend("/topic/orders", testMessage);
            return "WebSocket test message sent successfully!";
        } catch (Exception e) {
            return "Error sending WebSocket message: " + e.getMessage();
        }
    }
}