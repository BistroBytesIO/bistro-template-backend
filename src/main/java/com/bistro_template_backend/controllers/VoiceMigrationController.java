package com.bistro_template_backend.controllers;

import com.bistro_template_backend.services.FeatureFlagService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/voice/migration")
@RequiredArgsConstructor
public class VoiceMigrationController {

    private final FeatureFlagService featureFlagService;

    /**
     * Get current voice implementation status
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getStatus() {
        return ResponseEntity.ok(Map.of(
                "implementation", featureFlagService.getVoiceImplementation(),
                "realtimeEnabled", featureFlagService.isRealtimeVoiceEnabled(),
                "fallbackEnabled", featureFlagService.shouldFallbackToLegacy(),
                "message", "Voice implementation: " + featureFlagService.getVoiceImplementation()
        ));
    }

    /**
     * Check if realtime voice is available
     */
    @GetMapping("/availability")
    public ResponseEntity<Map<String, Object>> checkAvailability() {
        boolean available = featureFlagService.isRealtimeVoiceEnabled();

        return ResponseEntity.ok(Map.of(
                "available", available,
                "fallbackAvailable", featureFlagService.shouldFallbackToLegacy(),
                "recommendedImplementation", available ? "realtime" : "legacy"
        ));
    }
}