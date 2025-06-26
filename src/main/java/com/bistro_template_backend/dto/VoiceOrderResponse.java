package com.bistro_template_backend.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VoiceOrderResponse {

    private String sessionId;
    private Long orderId;
    private String orderStatus;
    private Double totalAmount;
    private Double subtotal;
    private Double tax;
    private Double serviceFee;
    private List<VoiceOrderItemResponse> items;
    private String specialInstructions;
    private LocalDateTime createdAt;
    private String customerName;
    private String customerEmail;
    private String customerPhone;
    private String paymentMethod;
    private boolean success;
    private String message;
    private String error;
    private List<String> validationErrors;
    private Map<String, Object> analytics;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class VoiceOrderItemResponse {
        private Long menuItemId;
        private String name;
        private Integer quantity;
        private Double price;
        private Double totalPrice;
        private List<String> customizations;
        private String notes;
    }
}