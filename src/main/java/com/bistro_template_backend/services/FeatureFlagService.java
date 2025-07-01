package com.bistro_template_backend.services;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class FeatureFlagService {

    @Value("${feature.realtime-voice.enabled:false}")
    private boolean realtimeVoiceEnabled;

    @Value("${feature.realtime-voice.fallback-to-legacy:true}")
    private boolean fallbackToLegacy;

    public boolean isRealtimeVoiceEnabled() {
        return realtimeVoiceEnabled;
    }

    public boolean shouldFallbackToLegacy() {
        return fallbackToLegacy;
    }

    public String getVoiceImplementation() {
        return realtimeVoiceEnabled ? "realtime" : "legacy";
    }
}