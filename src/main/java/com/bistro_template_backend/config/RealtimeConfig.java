package com.bistro_template_backend.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "openai.realtime")
@Data
public class RealtimeConfig {

    private String apiKey;
    private String baseUrl = "wss://api.openai.com/v1/realtime";
    private String model = "gpt-4o-realtime-preview-2025-06-03";
    private String voice = "ash";
    private boolean enableVad = true;
    private String vadType = "server_vad"; // server_vad or none
    private int audioSampleRate = 24000;
    private String audioFormat = "pcm16";
    private int maxConversationTurns = 50;
    private long sessionTimeoutMs = 1800000; // 30 minutes
    private boolean enableTranscription = true;
    private String transcriptionModel = "whisper-1";

    public String getWebSocketUrl() {
        return baseUrl + "?model=" + model;
    }
}