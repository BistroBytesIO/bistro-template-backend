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
public class VoiceAnalyticsResponse {

    private Map<String, Object> sessionMetrics;
    private Map<String, Object> voiceProcessingMetrics;
    private Map<String, Object> audioMetrics;
    private Map<String, Object> orderMetrics;
    private Map<String, Object> performanceMetrics;
    private Map<String, Object> dailyMetrics;
    private Map<String, Object> realTimeStats;
    private List<VoiceEventResponse> recentEvents;
    private LocalDateTime generatedAt;
    private String reportPeriod;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class VoiceEventResponse {
        private String type;
        private String sessionId;
        private LocalDateTime timestamp;
        private Map<String, Object> data;
        private String description;
    }
}