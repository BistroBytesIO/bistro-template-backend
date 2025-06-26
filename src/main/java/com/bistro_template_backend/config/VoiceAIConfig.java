package com.bistro_template_backend.config;

import com.theokanning.openai.service.OpenAiService;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.io.File;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executor;

@Configuration
@EnableCaching
@EnableAsync
@EnableScheduling
@Getter
public class VoiceAIConfig {

    // OpenAI API Configuration
    @Value("${openai.api.key}")
    private String openaiApiKey;

    @Value("${openai.api.organization:}")
    private String openaiOrganization;

    @Value("${openai.api.timeout:60000}")
    private int apiTimeout;

    @Value("${openai.api.retry.max-attempts:3}")
    private int maxRetryAttempts;

    @Value("${openai.api.retry.delay:1000}")
    private int retryDelay;

    // Model Configuration
    @Value("${openai.models.whisper:whisper-1}")
    private String whisperModel;

    @Value("${openai.models.gpt:gpt-4}")
    private String gptModel;

    @Value("${openai.models.tts:tts-1}")
    private String ttsModel;

    @Value("${openai.models.tts-voice:alloy}")
    private String ttsVoice;

    // Voice AI Configuration
    @Value("${voice.ai.max-audio-duration:300}")
    private int maxAudioDuration;

    @Value("${voice.ai.supported-formats:wav,mp3,m4a,webm,mp4,mpeg,mpga,ogg,oga}")
    private String supportedFormats;

    @Value("${voice.ai.max-file-size:25MB}")
    private String maxFileSize;

    @Value("${voice.ai.temp-dir:${java.io.tmpdir}/voice-ai}")
    private String tempDirectory;

    @Value("${voice.ai.cleanup-interval:3600000}")
    private long cleanupInterval;

    // Voice Session Configuration
    @Value("${voice.session.timeout:1800000}")
    private long sessionTimeout;

    @Value("${voice.session.max-turns:50}")
    private int maxSessionTurns;

    @Value("${voice.session.context-window:4000}")
    private int contextWindow;

    // Rate Limiting Configuration
    @Value("${voice.rate-limit.requests-per-minute:30}")
    private int requestsPerMinute;

    @Value("${voice.rate-limit.requests-per-hour:500}")
    private int requestsPerHour;

    @Value("${voice.rate-limit.audio-minutes-per-hour:60}")
    private int audioMinutesPerHour;

    @Bean
    public OpenAiService openAiService() {
        Duration timeout = Duration.ofMillis(apiTimeout);

        return new OpenAiService(openaiApiKey, timeout);
    }

    @Bean
    public Bucket requestRateLimitBucket() {
        Bandwidth limit = Bandwidth.classic(requestsPerMinute, Refill.intervally(requestsPerMinute, Duration.ofMinutes(1)));
        return Bucket.builder()
                .addLimit(limit)
                .build();
    }

    @Bean
    public Bucket hourlyRequestRateLimitBucket() {
        Bandwidth limit = Bandwidth.classic(requestsPerHour, Refill.intervally(requestsPerHour, Duration.ofHours(1)));
        return Bucket.builder()
                .addLimit(limit)
                .build();
    }

    @Bean
    public Bucket audioProcessingBucket() {
        Bandwidth limit = Bandwidth.classic(audioMinutesPerHour, Refill.intervally(audioMinutesPerHour, Duration.ofHours(1)));
        return Bucket.builder()
                .addLimit(limit)
                .build();
    }

    @Bean("voiceAITaskExecutor")
    public Executor voiceAITaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5);
        executor.setMaxPoolSize(10);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("VoiceAI-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }

    public List<String> getSupportedFormats() {
        return Arrays.asList(supportedFormats.split(","));
    }

    public long getMaxFileSizeBytes() {
        String size = maxFileSize.toUpperCase();
        if (size.endsWith("MB")) {
            return Long.parseLong(size.substring(0, size.length() - 2)) * 1024 * 1024;
        } else if (size.endsWith("KB")) {
            return Long.parseLong(size.substring(0, size.length() - 2)) * 1024;
        } else if (size.endsWith("GB")) {
            return Long.parseLong(size.substring(0, size.length() - 2)) * 1024 * 1024 * 1024;
        }
        return Long.parseLong(size);
    }

    public File getTempDirectory() {
        File dir = new File(tempDirectory);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir;
    }

    public String getSystemPrompt() {
        return """
            You are a helpful AI assistant for a restaurant voice ordering system. Your role is to:
            
            1. Help customers place orders by understanding their requests
            2. Ask clarifying questions about menu items, quantities, and customizations
            3. Provide information about menu items, prices, and availability
            4. Suggest popular items or alternatives when requested
            5. Confirm order details before finalizing
            6. Handle special dietary requirements and allergies
            7. Process payment confirmations
            
            Guidelines:
            - Be conversational and friendly
            - Ask one question at a time to avoid overwhelming the customer
            - Always confirm important details like quantities and customizations
            - If unsure about something, ask for clarification
            - Keep responses concise but helpful
            - Always confirm the total order before processing payment
            
            Restaurant Context:
            - This is a bistro/restaurant with a full menu of appetizers, mains, desserts, and beverages
            - We accept various payment methods including credit cards, Apple Pay, and Google Pay
            - Orders are prepared fresh and customers will receive confirmation via email
            
            Current conversation context will be provided with each request.
            """;
    }
}