package com.bistro_template_backend.dto.realtime;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ResponseCreateEvent {
    private String type = "response.create";
    private ResponseConfig response;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ResponseConfig {
        private String modalities;
        private String instructions;
        private String voice;
        @JsonProperty("output_audio_format")
        private String outputAudioFormat;
        private List<SessionUpdateEvent.SessionConfig.ToolDefinition> tools;
    }
}