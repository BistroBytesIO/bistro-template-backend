package com.bistro_template_backend.services;

import com.bistro_template_backend.models.ConversationTurn;

import java.util.List;

public interface ConversationService {
    ConversationTurn addTurn(String sessionId, String userMessage, String aiResponse);
    ConversationTurn addBranchedTurn(String sessionId, Long parentTurnId, String userMessage, String aiResponse);
    List<ConversationTurn> getConversationHistory(String sessionId, int windowSize);
    List<ConversationTurn> getConversationHistory(String sessionId);
}
