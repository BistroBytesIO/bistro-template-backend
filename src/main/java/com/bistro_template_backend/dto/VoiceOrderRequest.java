package com.bistro_template_backend.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VoiceOrderRequest {

    @NotBlank(message = "Session ID is required")
    private String sessionId;

    @Size(max = 100, message = "Customer name cannot exceed 100 characters")
    private String customerName;

    @Size(max = 20, message = "Phone number cannot exceed 20 characters")
    private String customerPhone;

    @Size(max = 50, message = "Payment method cannot exceed 50 characters")
    private String paymentMethod;

    @Size(max = 500, message = "Special instructions cannot exceed 500 characters")
    private String specialInstructions;

    private boolean sendConfirmationEmail = true;
    private boolean enableNotifications = true;

    // For adding specific items via API
    private List<VoiceOrderItemRequest> items;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class VoiceOrderItemRequest {
        
        @Min(value = 1, message = "Menu item ID must be positive")
        private Long menuItemId;

        @NotBlank(message = "Item name is required")
        @Size(max = 100, message = "Item name cannot exceed 100 characters")
        private String name;

        @Min(value = 1, message = "Quantity must be at least 1")
        @Max(value = 50, message = "Quantity cannot exceed 50")
        private Integer quantity;

        @Min(value = 0, message = "Price cannot be negative")
        private Double price;

        private List<String> customizations;

        @Size(max = 200, message = "Notes cannot exceed 200 characters")
        private String notes;
    }
}