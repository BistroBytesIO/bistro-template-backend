package com.bistro_template_backend.services.impl;

import com.bistro_template_backend.dto.SessionAnalytics;
import com.bistro_template_backend.models.ConversationTurn;
import com.bistro_template_backend.models.VoiceSession;
import com.bistro_template_backend.repositories.ConversationTurnRepository;
import com.bistro_template_backend.repositories.VoiceSessionRepository;
import com.bistro_template_backend.services.SessionAnalyticsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;

@Service
public class SessionAnalyticsServiceImpl implements SessionAnalyticsService {

    @Autowired
    private VoiceSessionRepository voiceSessionRepository;

    @Autowired
    private ConversationTurnRepository conversationTurnRepository;

    @Override
    public SessionAnalytics getAnalytics(String sessionId) {
        VoiceSession session = voiceSessionRepository.findBySessionId(sessionId).orElse(null);
        if (session == null) {
            return null; // Or throw an exception
        }

        SessionAnalytics analytics = new SessionAnalytics();
        analytics.setSessionId(sessionId);

        // Total Turns
        analytics.setTotalTurns(session.getTurnCount() != null ? session.getTurnCount() : 0);

        // Total Duration
        if (session.getCreatedAt() != null && session.getLastActivityAt() != null) {
            Duration duration = Duration.between(session.getCreatedAt(), session.getLastActivityAt());
            analytics.setTotalDurationSeconds(duration.getSeconds());
        } else {
            analytics.setTotalDurationSeconds(0);
        }

        // Average Processing Time and Confidence
        List<ConversationTurn> turns = conversationTurnRepository.findByVoiceSession_SessionIdOrderByTurnNumber(sessionId);
        if (!turns.isEmpty()) {
            double totalProcessingTime = turns.stream()
                    .mapToLong(turn -> turn.getProcessingTimeMs() != null ? turn.getProcessingTimeMs() : 0)
                    .sum();
            double totalConfidence = turns.stream()
                    .mapToDouble(turn -> turn.getTranscriptionConfidence() != null ? turn.getTranscriptionConfidence() : 0.0)
                    .sum();

            analytics.setAverageProcessingTimeMs(totalProcessingTime / turns.size());
            analytics.setAverageConfidence(totalConfidence / turns.size());
        } else {
            analytics.setAverageProcessingTimeMs(0.0);
            analytics.setAverageConfidence(0.0);
        }

        return analytics;
    }
}
