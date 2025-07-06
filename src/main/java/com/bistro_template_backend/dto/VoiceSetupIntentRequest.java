package com.bistro_template_backend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class VoiceSetupIntentRequest {
    
    @NotBlank(message = "Customer email is required")
    @Email(message = "Invalid email format")
    private String customerEmail;
    
    private String customerName;
    
    private String paymentMethodType; // "card", "apple_pay", "google_pay"
    
    private String voiceSessionId;
    
    private Boolean isVoiceSetup = true;
}