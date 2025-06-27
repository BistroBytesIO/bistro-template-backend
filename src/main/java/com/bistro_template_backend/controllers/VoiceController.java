package com.bistro_template_backend.controllers;

import com.bistro_template_backend.dto.TurnRequest;
import com.bistro_template_backend.models.ConversationTurn;
import com.bistro_template_backend.models.VoiceSession;
import com.bistro_template_backend.services.ConversationService;
import com.bistro_template_backend.services.SummarizationService;
import com.bistro_template_backend.services.VoiceSessionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import com.bistro_template_backend.dto.SessionAnalytics;
import com.bistro_template_backend.dto.TurnRequest;
import com.bistro_template_backend.models.ConversationTurn;
import com.bistro_template_backend.models.VoiceSession;
import com.bistro_template_backend.services.ConversationService;
import com.bistro_template_backend.services.SessionAnalyticsService;
import com.bistro_template_backend.services.SummarizationService;
import com.bistro_template_backend.services.VoiceSessionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/voice")
public class VoiceController {

    @Autowired
    private VoiceSessionService voiceSessionService;

    @Autowired
    private ConversationService conversationService;

    @Autowired
    private SummarizationService summarizationService;

    @Autowired
    private SessionAnalyticsService sessionAnalyticsService;

    @PostMapping("/session")
    public VoiceSession createSession(@RequestParam String customerId, @RequestParam String customerEmail) {
        return voiceSessionService.createSession(customerId, customerEmail);
    }

    @GetMapping("/session/{sessionId}")
    public VoiceSession getSession(@PathVariable String sessionId) {
        return voiceSessionService.getSession(sessionId);
    }

    @PostMapping("/session/{sessionId}/turn")
    public ConversationTurn addTurn(@PathVariable String sessionId, @RequestBody TurnRequest turnRequest) {
        // In a real application, you would get the AI response from a service
        String aiResponse = "This is a placeholder AI response.";
        return conversationService.addTurn(sessionId, turnRequest.getUserMessage(), aiResponse);
    }

    @PostMapping("/session/{sessionId}/turn/{parentTurnId}")
    public ConversationTurn addBranchedTurn(@PathVariable String sessionId, @PathVariable Long parentTurnId, @RequestBody TurnRequest turnRequest) {
        // In a real application, you would get the AI response from a service
        String aiResponse = "This is a placeholder AI response for a branched conversation.";
        return conversationService.addBranchedTurn(sessionId, parentTurnId, turnRequest.getUserMessage(), aiResponse);
    }

    @GetMapping("/session/{sessionId}/history")
    public List<ConversationTurn> getConversationHistory(@PathVariable String sessionId, @RequestParam(required = false, defaultValue = "0") int windowSize) {
        return conversationService.getConversationHistory(sessionId, windowSize);
    }

    @GetMapping("/session/{sessionId}/summary")
    public String getConversationSummary(@PathVariable String sessionId) {
        List<ConversationTurn> turns = conversationService.getConversationHistory(sessionId);
        String conversationText = turns.stream()
                .map(turn -> "User: " + turn.getUserMessage() + "\nAI: " + turn.getAiResponse())
                .collect(Collectors.joining("\n\n"));
        return summarizationService.summarize(conversationText);
    }

    @GetMapping("/session/{sessionId}/analytics")
    public SessionAnalytics getSessionAnalytics(@PathVariable String sessionId) {
        return sessionAnalyticsService.getAnalytics(sessionId);
    }

    @PostMapping("/session/{sessionId}/heartbeat")
    public void heartbeat(@PathVariable String sessionId) {
        voiceSessionService.heartbeat(sessionId);
    }

    @PostMapping("/session/{sessionId}/close")
    public void closeSession(@PathVariable String sessionId) {
        voiceSessionService.closeSession(sessionId);
    }
}
