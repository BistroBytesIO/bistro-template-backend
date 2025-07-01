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
public class VoiceTextProcessRequest {

    @NotBlank(message = "Text is required")
    @Size(max = 1000, message = "Text input cannot exceed 1000 characters")
    private String text;

    @Size(max = 10, message = "Language code cannot exceed 10 characters")
    private String language = "en";
}