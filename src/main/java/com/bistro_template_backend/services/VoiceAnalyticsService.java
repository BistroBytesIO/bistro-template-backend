package com.bistro_template_backend.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Service
@RequiredArgsConstructor
@Slf4j
public class VoiceAnalyticsService {

    // Metrics storage
    private final Map<String, AtomicLong> counters = new ConcurrentHashMap<>();
    private final Map<String, List<Double>> timings = new ConcurrentHashMap<>();
    private final Map<String, VoiceSessionAnalytics> sessionAnalytics = new ConcurrentHashMap<>();
    private final List<VoiceProcessingEvent> recentEvents = new ArrayList<>();
    
    // Constants
    private static final int MAX_RECENT_EVENTS = 1000;
    private static final int MAX_TIMING_SAMPLES = 100;

    /**
     * Record voice session start
     */
    public void recordSessionStart(String sessionId, String customerId) {
        incrementCounter("sessions.started");
        incrementCounter("sessions.started.daily." + getCurrentDate());
        
        VoiceSessionAnalytics analytics = new VoiceSessionAnalytics();
        analytics.sessionId = sessionId;
        analytics.customerId = customerId;
        analytics.startTime = LocalDateTime.now();
        analytics.turnCount = 0;
        
        sessionAnalytics.put(sessionId, analytics);
        
        recordEvent("SESSION_STARTED", sessionId, Map.of("customerId", customerId));
        log.debug("Recorded session start: {}", sessionId);
    }

    /**
     * Record voice session end
     */
    public void recordSessionEnd(String sessionId, String reason, boolean orderCreated) {
        incrementCounter("sessions.ended");
        incrementCounter("sessions.ended.daily." + getCurrentDate());
        
        if (orderCreated) {
            incrementCounter("sessions.converted");
            incrementCounter("sessions.converted.daily." + getCurrentDate());
        }
        
        VoiceSessionAnalytics analytics = sessionAnalytics.get(sessionId);
        if (analytics != null) {
            analytics.endTime = LocalDateTime.now();
            analytics.endReason = reason;
            analytics.orderCreated = orderCreated;
            
            // Calculate session duration
            long durationMs = java.time.Duration.between(analytics.startTime, analytics.endTime).toMillis();
            recordTiming("session.duration", durationMs / 1000.0); // Store in seconds
            
            recordEvent("SESSION_ENDED", sessionId, Map.of(
                "reason", reason,
                "orderCreated", orderCreated,
                "duration", durationMs / 1000.0,
                "turns", analytics.turnCount
            ));
        }
        
        log.debug("Recorded session end: {} - Reason: {}, Order: {}", sessionId, reason, orderCreated);
    }

    /**
     * Record voice processing metrics
     */
    public void recordVoiceProcessing(String sessionId, String operation, double duration, boolean success, String errorType) {
        incrementCounter("voice.processing.total");
        incrementCounter("voice.processing." + operation);
        
        if (success) {
            incrementCounter("voice.processing.success");
            incrementCounter("voice.processing." + operation + ".success");
            recordTiming("voice.processing." + operation + ".duration", duration);
        } else {
            incrementCounter("voice.processing.failed");
            incrementCounter("voice.processing." + operation + ".failed");
            if (errorType != null) {
                incrementCounter("voice.processing.errors." + errorType);
            }
        }
        
        recordEvent("VOICE_PROCESSING", sessionId, Map.of(
            "operation", operation,
            "duration", duration,
            "success", success,
            "errorType", errorType != null ? errorType : "none"
        ));
    }

    /**
     * Record conversation turn
     */
    public void recordConversationTurn(String sessionId, int turnNumber, String intent, double processingTime) {
        incrementCounter("conversation.turns");
        incrementCounter("conversation.turns.daily." + getCurrentDate());
        
        if (intent != null) {
            incrementCounter("conversation.intents." + intent);
        }
        
        recordTiming("conversation.processing_time", processingTime);
        
        VoiceSessionAnalytics analytics = sessionAnalytics.get(sessionId);
        if (analytics != null) {
            analytics.turnCount = turnNumber;
            analytics.lastActivity = LocalDateTime.now();
        }
        
        recordEvent("CONVERSATION_TURN", sessionId, Map.of(
            "turnNumber", turnNumber,
            "intent", intent != null ? intent : "unknown",
            "processingTime", processingTime
        ));
    }

    /**
     * Record audio processing metrics
     */
    public void recordAudioProcessing(String sessionId, String operation, double audioDuration, 
                                    double processingTime, boolean success) {
        incrementCounter("audio.processing.total");
        incrementCounter("audio.processing." + operation);
        
        recordTiming("audio.duration", audioDuration);
        recordTiming("audio.processing_time", processingTime);
        
        if (success) {
            incrementCounter("audio.processing.success");
            recordTiming("audio.processing_ratio", processingTime / audioDuration); // Processing time ratio
        } else {
            incrementCounter("audio.processing.failed");
        }
        
        recordEvent("AUDIO_PROCESSING", sessionId, Map.of(
            "operation", operation,
            "audioDuration", audioDuration,
            "processingTime", processingTime,
            "success", success
        ));
    }

    /**
     * Record order conversion metrics
     */
    public void recordOrderConversion(String sessionId, Long orderId, double orderValue, int itemCount) {
        incrementCounter("orders.created_from_voice");
        incrementCounter("orders.created_from_voice.daily." + getCurrentDate());
        
        recordTiming("orders.value", orderValue);
        recordTiming("orders.item_count", itemCount);
        
        recordEvent("ORDER_CONVERSION", sessionId, Map.of(
            "orderId", orderId,
            "orderValue", orderValue,
            "itemCount", itemCount
        ));
        
        log.info("Voice order conversion - Session: {}, Order: {}, Value: ${}", sessionId, orderId, orderValue);
    }

    /**
     * Record API usage metrics
     */
    public void recordApiUsage(String apiName, String operation, boolean success, double cost) {
        incrementCounter("api.calls." + apiName);
        incrementCounter("api.calls." + apiName + "." + operation);
        
        if (success) {
            incrementCounter("api.calls." + apiName + ".success");
        } else {
            incrementCounter("api.calls." + apiName + ".failed");
        }
        
        if (cost > 0) {
            recordTiming("api.costs." + apiName, cost);
        }
    }

    /**
     * Get comprehensive analytics report
     */
    public Map<String, Object> getAnalyticsReport() {
        Map<String, Object> report = new HashMap<>();
        
        // Session metrics
        Map<String, Object> sessionMetrics = new HashMap<>();
        sessionMetrics.put("totalStarted", getCounterValue("sessions.started"));
        sessionMetrics.put("totalEnded", getCounterValue("sessions.ended"));
        sessionMetrics.put("totalConverted", getCounterValue("sessions.converted"));
        sessionMetrics.put("activeCount", sessionAnalytics.size());
        sessionMetrics.put("conversionRate", calculateConversionRate());
        sessionMetrics.put("averageDuration", getAverageTiming("session.duration"));
        
        // Voice processing metrics
        Map<String, Object> voiceMetrics = new HashMap<>();
        voiceMetrics.put("totalProcessing", getCounterValue("voice.processing.total"));
        voiceMetrics.put("successRate", calculateSuccessRate("voice.processing"));
        voiceMetrics.put("averageWhisperTime", getAverageTiming("voice.processing.whisper.duration"));
        voiceMetrics.put("averageGptTime", getAverageTiming("voice.processing.gpt.duration"));
        voiceMetrics.put("averageTtsTime", getAverageTiming("voice.processing.tts.duration"));
        
        // Audio metrics
        Map<String, Object> audioMetrics = new HashMap<>();
        audioMetrics.put("averageAudioDuration", getAverageTiming("audio.duration"));
        audioMetrics.put("averageProcessingTime", getAverageTiming("audio.processing_time"));
        audioMetrics.put("averageProcessingRatio", getAverageTiming("audio.processing_ratio"));
        
        // Order metrics
        Map<String, Object> orderMetrics = new HashMap<>();
        orderMetrics.put("voiceOrdersCreated", getCounterValue("orders.created_from_voice"));
        orderMetrics.put("averageOrderValue", getAverageTiming("orders.value"));
        orderMetrics.put("averageItemCount", getAverageTiming("orders.item_count"));
        
        // Daily metrics
        Map<String, Object> dailyMetrics = getDailyMetrics();
        
        // Performance metrics
        Map<String, Object> performanceMetrics = new HashMap<>();
        performanceMetrics.put("averageConversationProcessingTime", getAverageTiming("conversation.processing_time"));
        performanceMetrics.put("totalConversationTurns", getCounterValue("conversation.turns"));
        
        report.put("sessions", sessionMetrics);
        report.put("voiceProcessing", voiceMetrics);
        report.put("audio", audioMetrics);
        report.put("orders", orderMetrics);
        report.put("daily", dailyMetrics);
        report.put("performance", performanceMetrics);
        report.put("generatedAt", LocalDateTime.now());
        
        return report;
    }

    /**
     * Get real-time statistics
     */
    public Map<String, Object> getRealTimeStats() {
        Map<String, Object> stats = new HashMap<>();
        
        stats.put("activeSessions", sessionAnalytics.size());
        stats.put("recentEvents", recentEvents.size());
        stats.put("todaysSessions", getCounterValue("sessions.started.daily." + getCurrentDate()));
        stats.put("todaysConversions", getCounterValue("sessions.converted.daily." + getCurrentDate()));
        stats.put("todaysOrders", getCounterValue("orders.created_from_voice.daily." + getCurrentDate()));
        
        return stats;
    }

    /**
     * Get recent events for monitoring
     */
    public List<VoiceProcessingEvent> getRecentEvents(int limit) {
        synchronized (recentEvents) {
            return new ArrayList<>(recentEvents.subList(
                Math.max(0, recentEvents.size() - limit), 
                recentEvents.size()
            ));
        }
    }

    /**
     * Get session analytics for a specific session
     */
    public VoiceSessionAnalytics getSessionAnalytics(String sessionId) {
        return sessionAnalytics.get(sessionId);
    }

    /**
     * Scheduled cleanup of old data
     */
    @Scheduled(fixedRate = 3600000) // Every hour
    public void cleanupOldData() {
        // Clean up old session analytics (older than 24 hours)
        LocalDateTime cutoff = LocalDateTime.now().minusHours(24);
        
        sessionAnalytics.entrySet().removeIf(entry -> {
            VoiceSessionAnalytics analytics = entry.getValue();
            return analytics.endTime != null && analytics.endTime.isBefore(cutoff);
        });
        
        // Clean up old events
        synchronized (recentEvents) {
            while (recentEvents.size() > MAX_RECENT_EVENTS) {
                recentEvents.remove(0);
            }
        }
        
        // Clean up old timing samples
        timings.values().forEach(list -> {
            while (list.size() > MAX_TIMING_SAMPLES) {
                list.remove(0);
            }
        });
        
        log.debug("Cleaned up old analytics data");
    }

    // Helper methods

    private void incrementCounter(String key) {
        counters.computeIfAbsent(key, k -> new AtomicLong(0)).incrementAndGet();
    }

    private long getCounterValue(String key) {
        AtomicLong counter = counters.get(key);
        return counter != null ? counter.get() : 0;
    }

    private void recordTiming(String key, double value) {
        timings.computeIfAbsent(key, k -> new ArrayList<>()).add(value);
    }

    private double getAverageTiming(String key) {
        List<Double> values = timings.get(key);
        if (values == null || values.isEmpty()) {
            return 0.0;
        }
        return values.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
    }

    private void recordEvent(String type, String sessionId, Map<String, Object> data) {
        VoiceProcessingEvent event = new VoiceProcessingEvent();
        event.type = type;
        event.sessionId = sessionId;
        event.timestamp = LocalDateTime.now();
        event.data = new HashMap<>(data);
        
        synchronized (recentEvents) {
            recentEvents.add(event);
            if (recentEvents.size() > MAX_RECENT_EVENTS) {
                recentEvents.remove(0);
            }
        }
    }

    private String getCurrentDate() {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
    }

    private double calculateConversionRate() {
        long started = getCounterValue("sessions.started");
        long converted = getCounterValue("sessions.converted");
        return started > 0 ? (double) converted / started * 100.0 : 0.0;
    }

    private double calculateSuccessRate(String operation) {
        long total = getCounterValue(operation + ".total");
        long success = getCounterValue(operation + ".success");
        return total > 0 ? (double) success / total * 100.0 : 0.0;
    }

    private Map<String, Object> getDailyMetrics() {
        String today = getCurrentDate();
        Map<String, Object> daily = new HashMap<>();
        
        daily.put("sessionsStarted", getCounterValue("sessions.started.daily." + today));
        daily.put("sessionsConverted", getCounterValue("sessions.converted.daily." + today));
        daily.put("ordersCreated", getCounterValue("orders.created_from_voice.daily." + today));
        daily.put("conversationTurns", getCounterValue("conversation.turns.daily." + today));
        
        return daily;
    }

    // Data classes
    @lombok.Data
    public static class VoiceSessionAnalytics {
        private String sessionId;
        private String customerId;
        private LocalDateTime startTime;
        private LocalDateTime endTime;
        private LocalDateTime lastActivity;
        private String endReason;
        private int turnCount;
        private boolean orderCreated;
    }

    @lombok.Data
    public static class VoiceProcessingEvent {
        private String type;
        private String sessionId;
        private LocalDateTime timestamp;
        private Map<String, Object> data;
    }
}