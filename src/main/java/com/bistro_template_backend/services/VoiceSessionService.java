package com.bistro_template_backend.services;

import com.bistro_template_backend.models.VoiceSession;

public interface VoiceSessionService {
    VoiceSession createSession(String customerId, String customerEmail);
    VoiceSession getSession(String sessionId);
    VoiceSession updateSession(VoiceSession session);
    void deleteSession(String sessionId);
    void expireSessions();
    void heartbeat(String sessionId);
    void closeSession(String sessionId);
}
