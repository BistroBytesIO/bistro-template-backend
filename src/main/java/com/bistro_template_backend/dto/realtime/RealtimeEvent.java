package com.bistro_template_backend.dto.realtime;

import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.Builder;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RealtimeEvent {
    private String type;
    @JsonProperty("event_id")
    private String eventId;
    private Map<String, Object> data;
}