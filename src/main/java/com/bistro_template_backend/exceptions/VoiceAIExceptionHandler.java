package com.bistro_template_backend.exceptions;

import com.theokanning.openai.OpenAiHttpException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeoutException;

@RestControllerAdvice(basePackages = "com.bistro_template_backend.controllers")
@Slf4j
public class VoiceAIExceptionHandler {

    /**
     * Handle OpenAI API exceptions
     */
    @ExceptionHandler(OpenAiHttpException.class)
    public ResponseEntity<?> handleOpenAiException(OpenAiHttpException e) {
        log.error("OpenAI API error: {}", e.getMessage(), e);
        
        Map<String, Object> errorResponse = new HashMap<>();
        errorResponse.put("error", "Voice AI service temporarily unavailable");
        errorResponse.put("type", "OPENAI_API_ERROR");
        errorResponse.put("timestamp", LocalDateTime.now());
        
        // Determine appropriate response based on OpenAI error type
        HttpStatus status = determineHttpStatusFromOpenAiError(e);
        
        if (e.statusCode == 429) {
            errorResponse.put("error", "Voice AI service rate limit exceeded. Please try again later.");
            errorResponse.put("retryAfter", "60 seconds");
        } else if (e.statusCode == 401) {
            errorResponse.put("error", "Voice AI service authentication error");
            log.error("OpenAI authentication failed - check API key configuration");
        } else if (e.statusCode >= 500) {
            errorResponse.put("error", "Voice AI service is experiencing issues. Please try again later.");
        } else {
            errorResponse.put("error", "Voice AI processing failed: " + e.getMessage());
        }
        
        errorResponse.put("statusCode", e.statusCode);
        
        return ResponseEntity.status(status).body(errorResponse);
    }

    /**
     * Handle audio file upload size exceeded
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<?> handleMaxUploadSizeExceeded(MaxUploadSizeExceededException e) {
        log.warn("Audio file upload size exceeded: {}", e.getMessage());
        
        Map<String, Object> errorResponse = new HashMap<>();
        errorResponse.put("error", "Audio file is too large. Maximum size is 25MB.");
        errorResponse.put("type", "FILE_SIZE_EXCEEDED");
        errorResponse.put("timestamp", LocalDateTime.now());
        errorResponse.put("maxSize", "25MB");
        
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(errorResponse);
    }

    /**
     * Handle voice session related exceptions
     */
    @ExceptionHandler(VoiceSessionException.class)
    public ResponseEntity<?> handleVoiceSessionException(VoiceSessionException e) {
        log.warn("Voice session error: {}", e.getMessage());
        
        Map<String, Object> errorResponse = new HashMap<>();
        errorResponse.put("error", e.getMessage());
        errorResponse.put("type", "VOICE_SESSION_ERROR");
        errorResponse.put("timestamp", LocalDateTime.now());
        
        if (e.getSessionId() != null) {
            errorResponse.put("sessionId", e.getSessionId());
        }
        
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }

    /**
     * Handle audio processing exceptions
     */
    @ExceptionHandler(AudioProcessingException.class)
    public ResponseEntity<?> handleAudioProcessingException(AudioProcessingException e) {
        log.error("Audio processing error: {}", e.getMessage(), e);
        
        Map<String, Object> errorResponse = new HashMap<>();
        errorResponse.put("error", "Failed to process audio file: " + e.getMessage());
        errorResponse.put("type", "AUDIO_PROCESSING_ERROR");
        errorResponse.put("timestamp", LocalDateTime.now());
        
        if (e.getFileName() != null) {
            errorResponse.put("fileName", e.getFileName());
        }
        
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(errorResponse);
    }

    /**
     * Handle rate limiting exceptions
     */
    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<?> handleRateLimitExceeded(RateLimitExceededException e) {
        log.warn("Rate limit exceeded: {}", e.getMessage());
        
        Map<String, Object> errorResponse = new HashMap<>();
        errorResponse.put("error", "Rate limit exceeded. Please slow down your requests.");
        errorResponse.put("type", "RATE_LIMIT_EXCEEDED");
        errorResponse.put("timestamp", LocalDateTime.now());
        errorResponse.put("retryAfter", e.getRetryAfterSeconds() + " seconds");
        
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(errorResponse);
    }

    /**
     * Handle timeout exceptions
     */
    @ExceptionHandler(TimeoutException.class)
    public ResponseEntity<?> handleTimeout(TimeoutException e) {
        log.error("Voice processing timeout: {}", e.getMessage());
        
        Map<String, Object> errorResponse = new HashMap<>();
        errorResponse.put("error", "Voice processing request timed out. Please try again.");
        errorResponse.put("type", "TIMEOUT_ERROR");
        errorResponse.put("timestamp", LocalDateTime.now());
        
        return ResponseEntity.status(HttpStatus.REQUEST_TIMEOUT).body(errorResponse);
    }

    /**
     * Handle illegal argument exceptions (validation errors)
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<?> handleIllegalArgument(IllegalArgumentException e) {
        log.warn("Validation error: {}", e.getMessage());
        
        Map<String, Object> errorResponse = new HashMap<>();
        errorResponse.put("error", e.getMessage());
        errorResponse.put("type", "VALIDATION_ERROR");
        errorResponse.put("timestamp", LocalDateTime.now());
        
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }

    /**
     * Handle voice order processing exceptions
     */
    @ExceptionHandler(VoiceOrderException.class)
    public ResponseEntity<?> handleVoiceOrderException(VoiceOrderException e) {
        log.error("Voice order processing error: {}", e.getMessage(), e);
        
        Map<String, Object> errorResponse = new HashMap<>();
        errorResponse.put("error", "Failed to process voice order: " + e.getMessage());
        errorResponse.put("type", "VOICE_ORDER_ERROR");
        errorResponse.put("timestamp", LocalDateTime.now());
        
        if (e.getOrderId() != null) {
            errorResponse.put("orderId", e.getOrderId());
        }
        
        if (e.getSessionId() != null) {
            errorResponse.put("sessionId", e.getSessionId());
        }
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
    }

    /**
     * Handle general voice AI exceptions
     */
    @ExceptionHandler(VoiceAIException.class)
    public ResponseEntity<?> handleVoiceAIException(VoiceAIException e) {
        log.error("Voice AI error: {}", e.getMessage(), e);
        
        Map<String, Object> errorResponse = new HashMap<>();
        errorResponse.put("error", e.getMessage());
        errorResponse.put("type", "VOICE_AI_ERROR");
        errorResponse.put("timestamp", LocalDateTime.now());
        
        HttpStatus status = e.isRetryable() ? HttpStatus.SERVICE_UNAVAILABLE : HttpStatus.INTERNAL_SERVER_ERROR;
        
        if (e.isRetryable()) {
            errorResponse.put("retryable", true);
            errorResponse.put("retryAfter", "30 seconds");
        }
        
        return ResponseEntity.status(status).body(errorResponse);
    }

    /**
     * Handle general runtime exceptions for voice endpoints
     */
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<?> handleRuntimeException(RuntimeException e) {
        // Only handle exceptions from voice-related endpoints
        if (!isVoiceRelatedRequest()) {
            throw e; // Re-throw for other controllers to handle
        }
        
        log.error("Unexpected runtime error in voice processing: {}", e.getMessage(), e);
        
        Map<String, Object> errorResponse = new HashMap<>();
        errorResponse.put("error", "An unexpected error occurred while processing your voice request");
        errorResponse.put("type", "INTERNAL_ERROR");
        errorResponse.put("timestamp", LocalDateTime.now());
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
    }

    /**
     * Determine HTTP status from OpenAI error
     */
    private HttpStatus determineHttpStatusFromOpenAiError(OpenAiHttpException e) {
        return switch (e.statusCode) {
            case 400 -> HttpStatus.BAD_REQUEST;
            case 401 -> HttpStatus.UNAUTHORIZED;
            case 403 -> HttpStatus.FORBIDDEN;
            case 404 -> HttpStatus.NOT_FOUND;
            case 429 -> HttpStatus.TOO_MANY_REQUESTS;
            case 500, 502, 503 -> HttpStatus.SERVICE_UNAVAILABLE;
            default -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }

    /**
     * Check if the current request is voice-related
     */
    private boolean isVoiceRelatedRequest() {
        // This is a simplified check - in a real implementation, you might
        // want to check the request path or use other context
        try {
            StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
            for (StackTraceElement element : stackTrace) {
                if (element.getClassName().contains("VoiceOrderController") ||
                    element.getClassName().contains("VoiceAI") ||
                    element.getClassName().contains("Voice")) {
                    return true;
                }
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    // Custom exception classes
    public static class VoiceSessionException extends RuntimeException {
        private final String sessionId;
        
        public VoiceSessionException(String message, String sessionId) {
            super(message);
            this.sessionId = sessionId;
        }
        
        public String getSessionId() {
            return sessionId;
        }
    }

    public static class AudioProcessingException extends RuntimeException {
        private final String fileName;
        
        public AudioProcessingException(String message, String fileName) {
            super(message);
            this.fileName = fileName;
        }
        
        public AudioProcessingException(String message, String fileName, Throwable cause) {
            super(message, cause);
            this.fileName = fileName;
        }
        
        public String getFileName() {
            return fileName;
        }
    }

    public static class RateLimitExceededException extends RuntimeException {
        private final int retryAfterSeconds;
        
        public RateLimitExceededException(String message, int retryAfterSeconds) {
            super(message);
            this.retryAfterSeconds = retryAfterSeconds;
        }
        
        public int getRetryAfterSeconds() {
            return retryAfterSeconds;
        }
    }

    public static class VoiceOrderException extends RuntimeException {
        private final String sessionId;
        private final Long orderId;
        
        public VoiceOrderException(String message, String sessionId, Long orderId) {
            super(message);
            this.sessionId = sessionId;
            this.orderId = orderId;
        }
        
        public VoiceOrderException(String message, String sessionId, Throwable cause) {
            super(message, cause);
            this.sessionId = sessionId;
            this.orderId = null;
        }
        
        public String getSessionId() {
            return sessionId;
        }
        
        public Long getOrderId() {
            return orderId;
        }
    }

    public static class VoiceAIException extends RuntimeException {
        private final boolean retryable;
        
        public VoiceAIException(String message, boolean retryable) {
            super(message);
            this.retryable = retryable;
        }
        
        public VoiceAIException(String message, boolean retryable, Throwable cause) {
            super(message, cause);
            this.retryable = retryable;
        }
        
        public boolean isRetryable() {
            return retryable;
        }
    }
}