package com.bistro_template_backend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class VoicePaymentRequest {
    
    @NotBlank(message = "Customer email is required")
    @Email(message = "Invalid email format")
    private String customerEmail;
    
    @NotNull(message = "Order ID is required")
    private Long orderId;
    
    @NotBlank(message = "Payment method ID is required")
    private String paymentMethodId;
    
    private String paymentMethodType; // "saved_card", "apple_pay", "google_pay"
    
    private Boolean savePaymentMethod = false;
    
    private String deviceInfo;
    
    private String voiceSessionId;
}