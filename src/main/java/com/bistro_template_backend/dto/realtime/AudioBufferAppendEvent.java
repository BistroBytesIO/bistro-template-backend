package com.bistro_template_backend.dto.realtime;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AudioBufferAppendEvent {
    private String type = "input_audio_buffer.append";
    private String audio; // Base64 encoded audio
}