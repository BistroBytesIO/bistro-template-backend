package com.bistro_template_backend.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VoiceSessionRequest {

    @NotBlank(message = "Customer ID is required")
    @Size(max = 50, message = "Customer ID cannot exceed 50 characters")
    private String customerId;

    @Email(message = "Valid email address is required")
    @NotBlank(message = "Customer email is required")
    @Size(max = 255, message = "Email cannot exceed 255 characters")
    private String customerEmail;

    @Size(max = 100, message = "Customer name cannot exceed 100 characters")
    private String customerName;

    @Size(max = 20, message = "Phone number cannot exceed 20 characters")
    private String customerPhone;

    @Size(max = 10, message = "Language code cannot exceed 10 characters")
    private String language = "en";

    private boolean enableAnalytics = true;
    private boolean enableNotifications = true;
}