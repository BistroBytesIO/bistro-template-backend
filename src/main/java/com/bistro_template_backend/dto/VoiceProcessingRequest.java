package com.bistro_template_backend.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.multipart.MultipartFile;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VoiceProcessingRequest {

    @NotBlank(message = "Session ID is required")
    private String sessionId;

    private MultipartFile audioFile;

    @Size(max = 10, message = "Language code cannot exceed 10 characters")
    private String language = "en";

    private boolean includeTTS = true;
    private boolean includeOrderUpdate = true;
    private boolean enableAnalytics = true;

    // For text-only requests (when audio is not provided)
    @Size(max = 1000, message = "Text input cannot exceed 1000 characters")
    private String textInput;
}