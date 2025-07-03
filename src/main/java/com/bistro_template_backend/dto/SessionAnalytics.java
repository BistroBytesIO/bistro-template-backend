package com.bistro_template_backend.dto;

import lombok.Data;

@Data
public class SessionAnalytics {
    private String sessionId;
    private long totalTurns;
    private long totalDurationSeconds;
    private double averageProcessingTimeMs;
    private double averageConfidence;
}
