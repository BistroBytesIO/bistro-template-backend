package com.bistro_template_backend.models;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "conversation_turns")
public class ConversationTurn {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "session_id", referencedColumnName = "sessionId")
    private VoiceSession voiceSession;

    @ManyToOne
    @JoinColumn(name = "parent_turn_id")
    private ConversationTurn parentTurn;

    private Integer turnNumber;

    @Column(columnDefinition = "TEXT")
    private String userMessage;

    @Column(columnDefinition = "TEXT")
    private String aiResponse;

    private Double transcriptionConfidence;

    private Long processingTimeMs;

    private LocalDateTime timestamp;

    private String intent;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String entitiesExtracted;
}
