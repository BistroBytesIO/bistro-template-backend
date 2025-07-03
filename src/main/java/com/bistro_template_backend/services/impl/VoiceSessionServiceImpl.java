package com.bistro_template_backend.services.impl;

import com.bistro_template_backend.models.VoiceSession;
import com.bistro_template_backend.models.VoiceSessionStatus;
import com.bistro_template_backend.repositories.VoiceSessionRepository;
import com.bistro_template_backend.services.VoiceSessionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class VoiceSessionServiceImpl implements VoiceSessionService {

    @Autowired
    private VoiceSessionRepository voiceSessionRepository;

    @Override
    public VoiceSession createSession(String customerId, String customerEmail) {
        VoiceSession session = new VoiceSession();
        session.setSessionId(UUID.randomUUID().toString());
        session.setCustomerId(customerId);
        session.setCustomerEmail(customerEmail);
        session.setStatus(VoiceSessionStatus.INITIALIZING);
        session.setCreatedAt(LocalDateTime.now());
        session.setLastActivityAt(LocalDateTime.now());
        // Set expiration time based on configuration
        session.setExpiresAt(LocalDateTime.now().plusMinutes(30)); 
        session.setTurnCount(0);
        session.setTotalDuration(0L);
        session.setSuccessRate(0.0);
        return voiceSessionRepository.save(session);
    }

    @Override
    public VoiceSession getSession(String sessionId) {
        return voiceSessionRepository.findBySessionId(sessionId).orElse(null);
    }

    @Override
    public VoiceSession updateSession(VoiceSession session) {
        session.setLastActivityAt(LocalDateTime.now());
        return voiceSessionRepository.save(session);
    }

    @Override
    public void deleteSession(String sessionId) {
        voiceSessionRepository.findBySessionId(sessionId).ifPresent(voiceSessionRepository::delete);
    }

    @Override
    @Scheduled(fixedRate = 60000) // Run every minute
    public void expireSessions() {
        voiceSessionRepository.findAll().forEach(session -> {
            if (session.getExpiresAt().isBefore(LocalDateTime.now()) && session.getStatus() != VoiceSessionStatus.CLOSED) {
                session.setStatus(VoiceSessionStatus.EXPIRED);
                voiceSessionRepository.save(session);
            }
        });
    }

    @Override
    public void heartbeat(String sessionId) {
        voiceSessionRepository.findBySessionId(sessionId).ifPresent(session -> {
            session.setLastActivityAt(LocalDateTime.now());
            session.setExpiresAt(LocalDateTime.now().plusMinutes(30)); // Extend expiration
            if (session.getStatus() == VoiceSessionStatus.EXPIRED) {
                session.setStatus(VoiceSessionStatus.ACTIVE);
            }
            voiceSessionRepository.save(session);
        });
    }

    @Override
    public void closeSession(String sessionId) {
        voiceSessionRepository.findBySessionId(sessionId).ifPresent(session -> {
            session.setStatus(VoiceSessionStatus.CLOSED);
            session.setLastActivityAt(LocalDateTime.now());
            if (session.getCreatedAt() != null) {
                session.setTotalDuration(Duration.between(session.getCreatedAt(), session.getLastActivityAt()).getSeconds());
            }
            voiceSessionRepository.save(session);
        });
    }
}

