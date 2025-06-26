package com.bistro_template_backend.services;

import com.bistro_template_backend.config.VoiceAIConfig;
import com.bistro_template_backend.exceptions.VoiceAIExceptionHandler.RateLimitExceededException;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class RateLimitingService {

    private final VoiceAIConfig voiceAIConfig;
    
    // Per-customer rate limiting buckets
    private final Map<String, Bucket> customerBuckets = new ConcurrentHashMap<>();
    private final Map<String, Bucket> customerAudioBuckets = new ConcurrentHashMap<>();
    
    // Per-session rate limiting buckets
    private final Map<String, Bucket> sessionBuckets = new ConcurrentHashMap<>();
    
    // Global rate limiting buckets
    private final Bucket globalRequestBucket;
    private final Bucket globalAudioBucket;

    public RateLimitingService(VoiceAIConfig voiceAIConfig) {
        this.voiceAIConfig = voiceAIConfig;
        
        // Initialize global buckets
        this.globalRequestBucket = createGlobalRequestBucket();
        this.globalAudioBucket = createGlobalAudioBucket();
    }

    /**
     * Check if customer can make voice request
     */
    public void checkCustomerRateLimit(String customerId) {
        if (customerId == null) {
            throw new IllegalArgumentException("Customer ID is required for rate limiting");
        }

        Bucket customerBucket = getCustomerBucket(customerId);
        if (!customerBucket.tryConsume(1)) {
            log.warn("Customer rate limit exceeded for customer: {}", customerId);
            throw new RateLimitExceededException(
                "Rate limit exceeded for customer. Please wait before making another request.", 60);
        }

        // Also check global rate limit
        if (!globalRequestBucket.tryConsume(1)) {
            log.warn("Global request rate limit exceeded");
            throw new RateLimitExceededException(
                "System is currently busy. Please try again later.", 30);
        }
    }

    /**
     * Check if customer can process audio (with duration-based limiting)
     */
    public void checkAudioProcessingRateLimit(String customerId, double audioDurationSeconds) {
        if (customerId == null) {
            throw new IllegalArgumentException("Customer ID is required for audio rate limiting");
        }

        // Convert duration to "minutes" for bucket consumption
        long durationTokens = Math.max(1, Math.round(audioDurationSeconds / 60.0));

        Bucket customerAudioBucket = getCustomerAudioBucket(customerId);
        if (!customerAudioBucket.tryConsume(durationTokens)) {
            log.warn("Customer audio rate limit exceeded for customer: {} ({}s audio)", 
                    customerId, audioDurationSeconds);
            throw new RateLimitExceededException(
                "Audio processing quota exceeded. Please wait before processing more audio.", 300);
        }

        // Also check global audio processing limit
        if (!globalAudioBucket.tryConsume(durationTokens)) {
            log.warn("Global audio processing rate limit exceeded");
            throw new RateLimitExceededException(
                "System audio processing capacity exceeded. Please try again later.", 60);
        }
    }

    /**
     * Check session-specific rate limits to prevent abuse
     */
    public void checkSessionRateLimit(String sessionId) {
        if (sessionId == null) {
            return; // No session-specific limiting for null sessions
        }

        Bucket sessionBucket = getSessionBucket(sessionId);
        if (!sessionBucket.tryConsume(1)) {
            log.warn("Session rate limit exceeded for session: {}", sessionId);
            throw new RateLimitExceededException(
                "Too many requests in this voice session. Please speak more slowly.", 10);
        }
    }

    /**
     * Get rate limit status for customer
     */
    public Map<String, Object> getCustomerRateLimitStatus(String customerId) {
        Bucket customerBucket = getCustomerBucket(customerId);
        Bucket customerAudioBucket = getCustomerAudioBucket(customerId);

        return Map.of(
            "customerId", customerId,
            "requestsAvailable", customerBucket.getAvailableTokens(),
            "audioMinutesAvailable", customerAudioBucket.getAvailableTokens(),
            "globalRequestsAvailable", globalRequestBucket.getAvailableTokens(),
            "globalAudioMinutesAvailable", globalAudioBucket.getAvailableTokens()
        );
    }

    /**
     * Get global rate limit status
     */
    public Map<String, Object> getGlobalRateLimitStatus() {
        return Map.of(
            "globalRequestsAvailable", globalRequestBucket.getAvailableTokens(),
            "globalAudioMinutesAvailable", globalAudioBucket.getAvailableTokens(),
            "activeCustomerBuckets", customerBuckets.size(),
            "activeSessionBuckets", sessionBuckets.size()
        );
    }

    /**
     * Cleanup inactive buckets to prevent memory leaks
     */
    public void cleanupInactiveBuckets() {
        // Simple cleanup - remove buckets that are full (indicating no recent activity)
        customerBuckets.entrySet().removeIf(entry -> entry.getValue().getAvailableTokens() >= getCustomerRequestLimit());
        customerAudioBuckets.entrySet().removeIf(entry -> entry.getValue().getAvailableTokens() >= getCustomerAudioLimit());
        sessionBuckets.entrySet().removeIf(entry -> entry.getValue().getAvailableTokens() >= getSessionRequestLimit());
        
        log.debug("Cleaned up rate limiting buckets. Active: customers={}, audio={}, sessions={}", 
                customerBuckets.size(), customerAudioBuckets.size(), sessionBuckets.size());
    }

    /**
     * Reset rate limits for a specific customer (admin function)
     */
    public void resetCustomerRateLimits(String customerId) {
        customerBuckets.remove(customerId);
        customerAudioBuckets.remove(customerId);
        log.info("Reset rate limits for customer: {}", customerId);
    }

    // Private helper methods

    private Bucket getCustomerBucket(String customerId) {
        return customerBuckets.computeIfAbsent(customerId, id -> createCustomerBucket());
    }

    private Bucket getCustomerAudioBucket(String customerId) {
        return customerAudioBuckets.computeIfAbsent(customerId, id -> createCustomerAudioBucket());
    }

    private Bucket getSessionBucket(String sessionId) {
        return sessionBuckets.computeIfAbsent(sessionId, id -> createSessionBucket());
    }

    private Bucket createCustomerBucket() {
        int requestsPerMinute = getCustomerRequestLimit();
        Bandwidth limit = Bandwidth.classic(requestsPerMinute, 
                Refill.intervally(requestsPerMinute, Duration.ofMinutes(1)));
        return Bucket.builder().addLimit(limit).build();
    }

    private Bucket createCustomerAudioBucket() {
        int audioMinutesPerHour = getCustomerAudioLimit();
        Bandwidth limit = Bandwidth.classic(audioMinutesPerHour, 
                Refill.intervally(audioMinutesPerHour, Duration.ofHours(1)));
        return Bucket.builder().addLimit(limit).build();
    }

    private Bucket createSessionBucket() {
        int requestsPerMinute = getSessionRequestLimit();
        Bandwidth limit = Bandwidth.classic(requestsPerMinute, 
                Refill.intervally(requestsPerMinute, Duration.ofMinutes(1)));
        return Bucket.builder().addLimit(limit).build();
    }

    private Bucket createGlobalRequestBucket() {
        int requestsPerMinute = voiceAIConfig.getRequestsPerMinute();
        Bandwidth limit = Bandwidth.classic(requestsPerMinute, 
                Refill.intervally(requestsPerMinute, Duration.ofMinutes(1)));
        return Bucket.builder().addLimit(limit).build();
    }

    private Bucket createGlobalAudioBucket() {
        int audioMinutesPerHour = voiceAIConfig.getAudioMinutesPerHour();
        Bandwidth limit = Bandwidth.classic(audioMinutesPerHour, 
                Refill.intervally(audioMinutesPerHour, Duration.ofHours(1)));
        return Bucket.builder().addLimit(limit).build();
    }

    private int getCustomerRequestLimit() {
        // Per-customer limit (more restrictive than global)
        return Math.max(1, voiceAIConfig.getRequestsPerMinute() / 5); // 20% of global limit per customer
    }

    private int getCustomerAudioLimit() {
        // Per-customer audio limit
        return Math.max(5, voiceAIConfig.getAudioMinutesPerHour() / 10); // 10% of global limit per customer
    }

    private int getSessionRequestLimit() {
        // Per-session limit (prevent rapid-fire requests)
        return 10; // 10 requests per minute per session
    }

    /**
     * API usage optimization - determine if request should be cached
     */
    public boolean shouldCacheResponse(String operation, String content) {
        // Cache TTS responses for repeated text
        if ("tts".equals(operation)) {
            return content != null && content.length() < 500; // Cache short responses
        }
        
        // Cache menu context responses
        if ("menu_context".equals(operation)) {
            return true;
        }
        
        // Don't cache transcriptions (always unique)
        if ("transcription".equals(operation)) {
            return false;
        }
        
        return false;
    }

    /**
     * Determine optimal batch size for processing
     */
    public int getOptimalBatchSize(String operation) {
        return switch (operation) {
            case "tts" -> 5; // Process up to 5 TTS requests together
            case "transcription" -> 1; // Process transcriptions individually
            case "conversation" -> 3; // Batch conversation turns
            default -> 1;
        };
    }

    /**
     * Check if system is under high load
     */
    public boolean isSystemUnderHighLoad() {
        long globalRequestsRemaining = globalRequestBucket.getAvailableTokens();
        long globalAudioRemaining = globalAudioBucket.getAvailableTokens();
        
        double requestUtilization = 1.0 - (double) globalRequestsRemaining / voiceAIConfig.getRequestsPerMinute();
        double audioUtilization = 1.0 - (double) globalAudioRemaining / voiceAIConfig.getAudioMinutesPerHour();
        
        return requestUtilization > 0.8 || audioUtilization > 0.8; // 80% utilization threshold
    }

    /**
     * Get optimization recommendations
     */
    public Map<String, Object> getOptimizationRecommendations() {
        boolean highLoad = isSystemUnderHighLoad();
        
        Map<String, Object> recommendations = new HashMap<>();
        recommendations.put("highLoad", highLoad);
        
        if (highLoad) {
            recommendations.put("recommendations", List.of(
                "Enable aggressive caching",
                "Increase batch processing",
                "Reduce TTS quality for faster processing",
                "Implement request queuing",
                "Consider scaling infrastructure"
            ));
        } else {
            recommendations.put("recommendations", List.of(
                "System operating normally",
                "Standard caching policies active",
                "No optimization changes needed"
            ));
        }
        
        return recommendations;
    }
}