package com.bistro_template_backend.services;

import com.bistro_template_backend.dto.OrderNotificationDTO;
import com.bistro_template_backend.models.Order;
import com.bistro_template_backend.services.VoiceSessionManager.VoiceSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;

@Service
public class WebSocketOrderService {

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    /**
     * Send new order notification to all connected admin clients
     */
    public void notifyNewOrder(Order order) {
        OrderNotificationDTO notification = new OrderNotificationDTO(
                order.getId(),
                order.getCustomerName(),
                order.getCustomerEmail(),
                order.getTotalAmount(),
                order.getOrderDate(),
                "NEW_ORDER",
                "New order #" + order.getId() + " from " + order.getCustomerName()
        );

        // Send to all subscribers of /topic/orders
        messagingTemplate.convertAndSend("/topic/orders", notification);
    }

    /**
     * Send order status update notification
     */
    public void notifyOrderStatusUpdate(Order order, String status) {
        OrderNotificationDTO notification = new OrderNotificationDTO(
                order.getId(),
                order.getCustomerName(),
                order.getCustomerEmail(),
                order.getTotalAmount(),
                order.getOrderDate(),
                "ORDER_STATUS_UPDATE",
                "Order #" + order.getId() + " status updated to: " + status
        );

        messagingTemplate.convertAndSend("/topic/orders", notification);
    }

    /**
     * Send general order update notification
     */
    public void notifyOrderUpdate(Order order, String message) {
        OrderNotificationDTO notification = new OrderNotificationDTO(
                order.getId(),
                order.getCustomerName(),
                order.getCustomerEmail(),
                order.getTotalAmount(),
                order.getOrderDate(),
                "ORDER_UPDATE",
                message
        );

        messagingTemplate.convertAndSend("/topic/orders", notification);
    }

    /**
     * Send voice session start notification
     */
    public void notifyVoiceSessionStarted(VoiceSession session) {
        Map<String, Object> notification = Map.of(
                "type", "VOICE_SESSION_STARTED",
                "sessionId", session.getSessionId(),
                "customerId", session.getCustomerId(),
                "customerEmail", session.getCustomerEmail(),
                "timestamp", LocalDateTime.now(),
                "message", "Voice ordering session started for " + session.getCustomerEmail()
        );

        messagingTemplate.convertAndSend("/topic/voice-sessions", notification);
    }

    /**
     * Send voice session activity notification
     */
    public void notifyVoiceSessionActivity(String sessionId, String activity, String details) {
        Map<String, Object> notification = Map.of(
                "type", "VOICE_SESSION_ACTIVITY",
                "sessionId", sessionId,
                "activity", activity,
                "details", details,
                "timestamp", LocalDateTime.now(),
                "message", "Voice session activity: " + activity
        );

        messagingTemplate.convertAndSend("/topic/voice-sessions", notification);
    }

    /**
     * Send voice order update notification
     */
    public void notifyVoiceOrderUpdate(String sessionId, String customerEmail, 
                                     int itemCount, String action) {
        Map<String, Object> notification = Map.of(
                "type", "VOICE_ORDER_UPDATE",
                "sessionId", sessionId,
                "customerEmail", customerEmail,
                "itemCount", itemCount,
                "action", action,
                "timestamp", LocalDateTime.now(),
                "message", "Voice order updated: " + action + " (Items: " + itemCount + ")"
        );

        messagingTemplate.convertAndSend("/topic/voice-orders", notification);
    }

    /**
     * Send voice session end notification
     */
    public void notifyVoiceSessionEnded(String sessionId, String reason, boolean orderCreated, Long orderId) {
        Map<String, Object> notification = Map.of(
                "type", "VOICE_SESSION_ENDED",
                "sessionId", sessionId,
                "reason", reason,
                "orderCreated", orderCreated,
                "orderId", orderId != null ? orderId : 0L,
                "timestamp", LocalDateTime.now(),
                "message", "Voice session ended: " + reason + 
                          (orderCreated ? " (Order created: " + orderId + ")" : "")
        );

        messagingTemplate.convertAndSend("/topic/voice-sessions", notification);
    }

    /**
     * Send voice processing status notification
     */
    public void notifyVoiceProcessingStatus(String sessionId, String status, String details) {
        Map<String, Object> notification = Map.of(
                "type", "VOICE_PROCESSING_STATUS",
                "sessionId", sessionId,
                "status", status,
                "details", details,
                "timestamp", LocalDateTime.now(),
                "message", "Voice processing: " + status
        );

        messagingTemplate.convertAndSend("/topic/voice-processing", notification);
    }

    /**
     * Send voice AI error notification
     */
    public void notifyVoiceAIError(String sessionId, String errorType, String errorMessage) {
        Map<String, Object> notification = Map.of(
                "type", "VOICE_AI_ERROR",
                "sessionId", sessionId,
                "errorType", errorType,
                "errorMessage", errorMessage,
                "timestamp", LocalDateTime.now(),
                "message", "Voice AI error: " + errorType
        );

        messagingTemplate.convertAndSend("/topic/voice-errors", notification);
    }

    /**
     * Send voice session statistics update
     */
    public void notifyVoiceSessionStats(Map<String, Object> stats) {
        Map<String, Object> notification = Map.of(
                "type", "VOICE_SESSION_STATS",
                "stats", stats,
                "timestamp", LocalDateTime.now(),
                "message", "Voice session statistics updated"
        );

        messagingTemplate.convertAndSend("/topic/voice-stats", notification);
    }
}