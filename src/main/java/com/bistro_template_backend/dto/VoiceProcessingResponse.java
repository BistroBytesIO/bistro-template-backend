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
public class VoiceProcessingResponse {

    private String sessionId;
    private String transcription;
    private String aiResponse;
    private byte[] audioResponse;
    private String audioResponseUrl;
    private boolean orderUpdated;
    private String orderAction;
    private Map<String, Object> orderSummary;
    private LocalDateTime processingTime;
    private double processingDurationMs;
    private boolean success;
    private String error;
    private String errorType;
    private Map<String, Object> analytics;
    private Map<String, Object> rateLimitStatus;
}