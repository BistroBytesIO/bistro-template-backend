package com.bistro_template_backend.repositories;

import com.bistro_template_backend.models.ConversationTurn;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ConversationTurnRepository extends JpaRepository<ConversationTurn, Long> {
    List<ConversationTurn> findByVoiceSession_SessionIdOrderByTurnNumber(String sessionId);
}
