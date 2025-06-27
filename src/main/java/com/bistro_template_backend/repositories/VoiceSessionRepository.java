package com.bistro_template_backend.repositories;

import com.bistro_template_backend.models.VoiceSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface VoiceSessionRepository extends JpaRepository<VoiceSession, Long> {
    Optional<VoiceSession> findBySessionId(String sessionId);
}
