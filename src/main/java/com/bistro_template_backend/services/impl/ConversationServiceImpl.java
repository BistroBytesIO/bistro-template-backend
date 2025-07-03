package com.bistro_template_backend.services.impl;

import com.bistro_template_backend.models.ConversationTurn;
import com.bistro_template_backend.models.VoiceSession;
import com.bistro_template_backend.repositories.ConversationTurnRepository;
import com.bistro_template_backend.services.ConversationService;
import com.bistro_template_backend.services.VoiceSessionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class ConversationServiceImpl implements ConversationService {

    @Value("${conversation.window.size:10}")
    private int defaultWindowSize;

    @Autowired
    private ConversationTurnRepository conversationTurnRepository;

    @Autowired
    private VoiceSessionService voiceSessionService;

    @Override
    public ConversationTurn addTurn(String sessionId, String userMessage, String aiResponse) {
        return addTurn(sessionId, null, userMessage, aiResponse);
    }

    @Override
    public ConversationTurn addBranchedTurn(String sessionId, Long parentTurnId, String userMessage, String aiResponse) {
        return addTurn(sessionId, parentTurnId, userMessage, aiResponse);
    }

    private ConversationTurn addTurn(String sessionId, Long parentTurnId, String userMessage, String aiResponse) {
        VoiceSession session = voiceSessionService.getSession(sessionId);
        if (session == null) {
            // Handle session not found
            return null;
        }

        ConversationTurn turn = new ConversationTurn();
        turn.setVoiceSession(session);
        turn.setUserMessage(userMessage);
        turn.setAiResponse(aiResponse);
        turn.setTimestamp(LocalDateTime.now());

        if (parentTurnId != null) {
            conversationTurnRepository.findById(parentTurnId).ifPresent(turn::setParentTurn);
        }

        List<ConversationTurn> turns = getConversationHistory(sessionId, 0);
        turn.setTurnNumber(turns.size() + 1);

        // Populate new fields with dummy data for now
        turn.setTranscriptionConfidence(Math.random());
        turn.setProcessingTimeMs((long) (Math.random() * 1000));
        turn.setIntent("ORDER_DRINK");
        turn.setEntitiesExtracted("{ \"drink\": \"coffee\" }");

        ConversationTurn savedTurn = conversationTurnRepository.save(turn);

        // Update session
        session.setTurnCount(session.getTurnCount() == null ? 1 : session.getTurnCount() + 1);
        voiceSessionService.updateSession(session);

        return savedTurn;
    }

    @Override
    public List<ConversationTurn> getConversationHistory(String sessionId, int windowSize) {
        List<ConversationTurn> turns = conversationTurnRepository.findByVoiceSession_SessionIdOrderByTurnNumber(sessionId);
        if (windowSize > 0 && turns.size() > windowSize) {
            return turns.subList(turns.size() - windowSize, turns.size());
        }
        return turns;
    }

    @Override
    public List<ConversationTurn> getConversationHistory(String sessionId) {
        return getConversationHistory(sessionId, defaultWindowSize);
    }
}
