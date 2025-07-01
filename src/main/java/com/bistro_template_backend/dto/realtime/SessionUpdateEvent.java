package com.bistro_template_backend.dto.realtime;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SessionUpdateEvent {
    private String type = "session.update";
    private SessionConfig session;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class SessionConfig {
        private String modalities;
        private String instructions;
        private String voice;
        @JsonProperty("input_audio_format")
        private String inputAudioFormat;
        @JsonProperty("output_audio_format")
        private String outputAudioFormat;
        @JsonProperty("input_audio_transcription")
        private TranscriptionConfig inputAudioTranscription;
        @JsonProperty("turn_detection")
        private TurnDetectionConfig turnDetection;
        private List<ToolDefinition> tools;

        @Data
        @NoArgsConstructor
        @AllArgsConstructor
        public static class TranscriptionConfig {
            private String model;
        }

        @Data
        @NoArgsConstructor
        @AllArgsConstructor
        public static class TurnDetectionConfig {
            private String type;
            private Double threshold;
            @JsonProperty("prefix_padding_ms")
            private Integer prefixPaddingMs;
            @JsonProperty("silence_duration_ms")
            private Integer silenceDurationMs;
        }

        @Data
        @NoArgsConstructor
        @AllArgsConstructor
        public static class ToolDefinition {
            private String type = "function";
            private String name;
            private String description;
            private Map<String, Object> parameters;
        }
    }
}