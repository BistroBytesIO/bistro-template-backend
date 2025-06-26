package com.bistro_template_backend.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VoiceSessionResponse {

    private String sessionId;
    private String customerId;
    private String customerEmail;
    private LocalDateTime createdAt;
    private String status;
    private String message;
    private Map<String, Object> menuContext;
    private String instructions;
    private Map<String, Object> rateLimits;
    private boolean success;
    private String error;
}