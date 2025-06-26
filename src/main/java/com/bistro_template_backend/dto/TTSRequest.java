package com.bistro_template_backend.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TTSRequest {

    @NotBlank(message = "Text is required for text-to-speech conversion")
    @Size(max = 1000, message = "Text cannot exceed 1000 characters")
    private String text;

    @Size(max = 20, message = "Voice cannot exceed 20 characters")
    private String voice = "alloy";

    @Size(max = 10, message = "Format cannot exceed 10 characters")
    private String format = "mp3";

    private Double speed = 1.0;

    private boolean enableCaching = true;
}