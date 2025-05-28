package com.bistro_template_backend.services;

import com.bistro_template_backend.dto.OrderNotificationDTO;
import com.bistro_template_backend.models.Order;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

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
}