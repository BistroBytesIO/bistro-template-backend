package com.bistro_template_backend.services;

import com.bistro_template_backend.config.VoiceAIConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.theokanning.openai.audio.CreateSpeechRequest;
import com.theokanning.openai.audio.CreateTranscriptionRequest;
import com.theokanning.openai.completion.chat.ChatCompletionRequest;
import com.theokanning.openai.completion.chat.ChatCompletionResult;
import com.theokanning.openai.completion.chat.ChatMessage;
import com.theokanning.openai.completion.chat.ChatMessageRole;
import com.theokanning.openai.service.OpenAiService;
import io.github.bucket4j.Bucket;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Service
@RequiredArgsConstructor
@Slf4j
public class VoiceAIService {

    private final OpenAiService openAiService;
    private final VoiceAIConfig voiceAIConfig;
    
    @Qualifier("requestRateLimitBucket")
    private final Bucket requestRateLimitBucket;
    
    @Qualifier("hourlyRequestRateLimitBucket")
    private final Bucket hourlyRequestRateLimitBucket;
    
    @Qualifier("audioProcessingBucket")
    private final Bucket audioProcessingBucket;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Convert speech to text using OpenAI Whisper
     */
    public String speechToText(MultipartFile audioFile, String language) throws Exception {
        if (!checkRateLimit()) {
            throw new RuntimeException("Rate limit exceeded. Please try again later.");
        }

        // Validate audio file
        validateAudioFile(audioFile);

        // Save audio file temporarily
        File tempFile = saveAudioTemporarily(audioFile);
        
        try {
            log.info("Starting speech-to-text conversion for file: {}", audioFile.getOriginalFilename());
            
            CreateTranscriptionRequest request = CreateTranscriptionRequest.builder()
                    .model(voiceAIConfig.getWhisperModel())
                    .language(language)
                    .responseFormat("json")
                    .temperature(0.0)
                    .build();

            String transcription = openAiService.createTranscription(request, tempFile).getText();
            
            log.info("Speech-to-text conversion completed. Transcription length: {} characters", transcription.length());
            return transcription;
            
        } finally {
            // Clean up temporary file
            if (tempFile.exists()) {
                tempFile.delete();
            }
        }
    }

    /**
     * Process conversation using GPT-4
     */
    public String processConversation(String userMessage, List<String> conversationHistory, 
                                    String menuContext, String orderContext) throws Exception {
        if (!checkRateLimit()) {
            throw new RuntimeException("Rate limit exceeded. Please try again later.");
        }

        log.info("Processing conversation with GPT-4. User message length: {}", userMessage.length());

        List<ChatMessage> messages = buildChatMessages(userMessage, conversationHistory, menuContext, orderContext);

        ChatCompletionRequest request = ChatCompletionRequest.builder()
                .model(voiceAIConfig.getGptModel())
                .messages(messages)
                .maxTokens(500)
                .temperature(0.7)
                .presencePenalty(0.1)
                .frequencyPenalty(0.1)
                .build();

        ChatCompletionResult result = openAiService.createChatCompletion(request);
        String response = result.getChoices().get(0).getMessage().getContent();
        
        log.info("GPT-4 response generated. Response length: {}", response.length());
        return response;
    }

    /**
     * Convert text to speech using OpenAI TTS
     */
    @Cacheable(value = "tts-cache", key = "#text.hashCode()")
    public byte[] textToSpeech(String text) throws Exception {
        if (!checkRateLimit()) {
            throw new RuntimeException("Rate limit exceeded. Please try again later.");
        }

        log.info("Converting text to speech. Text length: {}", text.length());

        CreateSpeechRequest request = CreateSpeechRequest.builder()
                .model(voiceAIConfig.getTtsModel())
                .input(text)
                .voice(voiceAIConfig.getTtsVoice())
                .responseFormat("mp3")
                .speed(1.0)
                .build();

        okhttp3.ResponseBody responseBody = openAiService.createSpeech(request);
        try {
            byte[] audioBytes = responseBody.bytes();
            log.info("Text-to-speech conversion completed. Audio size: {} bytes", audioBytes.length);
            return audioBytes;
        } finally {
            responseBody.close();
        }
    }

    /**
     * Process complete voice interaction (speech-to-text + conversation + text-to-speech)
     */
    @Async("voiceAITaskExecutor")
    public CompletableFuture<VoiceProcessingResult> processVoiceInteraction(
            MultipartFile audioFile, String sessionId, String language,
            List<String> conversationHistory, String menuContext, String orderContext) {
        
        try {
            log.info("Starting complete voice interaction processing for session: {}", sessionId);
            
            // Step 1: Speech to Text
            String transcription = speechToText(audioFile, language);
            
            // Step 2: Process with GPT-4
            String aiResponse = processConversation(transcription, conversationHistory, menuContext, orderContext);
            
            // Step 3: Text to Speech
            byte[] audioResponse = textToSpeech(aiResponse);
            
            VoiceProcessingResult result = VoiceProcessingResult.builder()
                    .sessionId(sessionId)
                    .transcription(transcription)
                    .aiResponse(aiResponse)
                    .audioResponse(audioResponse)
                    .processingTime(System.currentTimeMillis())
                    .success(true)
                    .build();
            
            log.info("Voice interaction processing completed successfully for session: {}", sessionId);
            return CompletableFuture.completedFuture(result);
            
        } catch (Exception e) {
            log.error("Error processing voice interaction for session: {}", sessionId, e);
            
            VoiceProcessingResult result = VoiceProcessingResult.builder()
                    .sessionId(sessionId)
                    .error(e.getMessage())
                    .processingTime(System.currentTimeMillis())
                    .success(false)
                    .build();
            
            return CompletableFuture.completedFuture(result);
        }
    }

    /**
     * Process text-only interaction (conversation processing without audio)
     */
    @Async("voiceAITaskExecutor")
    public CompletableFuture<VoiceProcessingResult> processTextInteraction(
            String text, String sessionId, String language,
            List<String> conversationHistory, String menuContext, String orderContext) {
        
        try {
            log.info("Starting text interaction processing for session: {}", sessionId);
            
            // Step 1: Process with GPT-4 (using provided text directly)
            String aiResponse = processConversation(text, conversationHistory, menuContext, orderContext);
            
            VoiceProcessingResult result = VoiceProcessingResult.builder()
                    .sessionId(sessionId)
                    .transcription(text)
                    .aiResponse(aiResponse)
                    .processingTime(System.currentTimeMillis())
                    .success(true)
                    .build();
            
            log.info("Text interaction processing completed successfully for session: {}", sessionId);
            return CompletableFuture.completedFuture(result);
            
        } catch (Exception e) {
            log.error("Error processing text interaction for session: {}", sessionId, e);
            
            VoiceProcessingResult result = VoiceProcessingResult.builder()
                    .sessionId(sessionId)
                    .error(e.getMessage())
                    .processingTime(System.currentTimeMillis())
                    .success(false)
                    .build();
            
            return CompletableFuture.completedFuture(result);
        }
    }

    /**
     * Validate audio file format and size
     */
    private void validateAudioFile(MultipartFile audioFile) throws Exception {
        if (audioFile.isEmpty()) {
            throw new IllegalArgumentException("Audio file is empty");
        }

        // Check file size
        if (audioFile.getSize() > voiceAIConfig.getMaxFileSizeBytes()) {
            throw new IllegalArgumentException("Audio file size exceeds maximum allowed size");
        }

        // Check file format
        String originalFilename = audioFile.getOriginalFilename();
        if (originalFilename == null) {
            throw new IllegalArgumentException("Audio file name is required");
        }

        String extension = originalFilename.substring(originalFilename.lastIndexOf('.') + 1).toLowerCase();
        if (!voiceAIConfig.getSupportedFormats().contains(extension)) {
            throw new IllegalArgumentException("Unsupported audio format: " + extension);
        }
    }

    /**
     * Save audio file temporarily for processing
     */
    private File saveAudioTemporarily(MultipartFile audioFile) throws IOException {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String uniqueId = UUID.randomUUID().toString().substring(0, 8);
        String extension = audioFile.getOriginalFilename().substring(audioFile.getOriginalFilename().lastIndexOf('.'));
        
        String filename = String.format("audio_%s_%s%s", timestamp, uniqueId, extension);
        File tempFile = new File(voiceAIConfig.getTempDirectory(), filename);
        
        try (FileOutputStream fos = new FileOutputStream(tempFile)) {
            fos.write(audioFile.getBytes());
        }
        
        return tempFile;
    }

    /**
     * Build chat messages for GPT-4 conversation
     */
    private List<ChatMessage> buildChatMessages(String userMessage, List<String> conversationHistory,
                                              String menuContext, String orderContext) {
        List<ChatMessage> messages = new ArrayList<>();
        
        // System prompt
        messages.add(new ChatMessage(ChatMessageRole.SYSTEM.value(), voiceAIConfig.getSystemPrompt()));
        
        // Add menu context if available
        if (menuContext != null && !menuContext.trim().isEmpty()) {
            messages.add(new ChatMessage(ChatMessageRole.SYSTEM.value(), 
                "Current Menu Context:\n" + menuContext));
        }
        
        // Add current order context if available
        if (orderContext != null && !orderContext.trim().isEmpty()) {
            messages.add(new ChatMessage(ChatMessageRole.SYSTEM.value(), 
                "Current Order Context:\n" + orderContext));
        }
        
        // Add conversation history (keeping it manageable)
        if (conversationHistory != null && !conversationHistory.isEmpty()) {
            int startIndex = Math.max(0, conversationHistory.size() - 10); // Keep last 10 exchanges
            for (int i = startIndex; i < conversationHistory.size(); i++) {
                String message = conversationHistory.get(i);
                ChatMessageRole role = (i % 2 == 0) ? ChatMessageRole.USER : ChatMessageRole.ASSISTANT;
                messages.add(new ChatMessage(role.value(), message));
            }
        }
        
        // Add current user message
        messages.add(new ChatMessage(ChatMessageRole.USER.value(), userMessage));
        
        return messages;
    }

    /**
     * Check rate limits before making API calls
     */
    private boolean checkRateLimit() {
        return requestRateLimitBucket.tryConsume(1) && hourlyRequestRateLimitBucket.tryConsume(1);
    }

    /**
     * Get rate limit status
     */
    public Map<String, Object> getRateLimitStatus() {
        return Map.of(
            "requestsPerMinuteAvailable", requestRateLimitBucket.getAvailableTokens(),
            "requestsPerHourAvailable", hourlyRequestRateLimitBucket.getAvailableTokens(),
            "audioProcessingAvailable", audioProcessingBucket.getAvailableTokens()
        );
    }

    /**
     * Clean up temporary files
     */
    public void cleanupTempFiles() {
        try {
            File tempDir = voiceAIConfig.getTempDirectory();
            if (tempDir.exists() && tempDir.isDirectory()) {
                File[] files = tempDir.listFiles();
                if (files != null) {
                    long cutoffTime = System.currentTimeMillis() - voiceAIConfig.getCleanupInterval();
                    for (File file : files) {
                        if (file.lastModified() < cutoffTime) {
                            if (file.delete()) {
                                log.debug("Cleaned up temporary file: {}", file.getName());
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("Error cleaning up temporary files", e);
        }
    }

    /**
     * Result class for voice processing operations
     */
    @lombok.Builder
    @lombok.Data
    public static class VoiceProcessingResult {
        private String sessionId;
        private String transcription;
        private String aiResponse;
        private byte[] audioResponse;
        private String error;
        private long processingTime;
        private boolean success;
    }
}