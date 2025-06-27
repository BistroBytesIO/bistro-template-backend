package com.bistro_template_backend.models;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "voice_sessions")
public class VoiceSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String sessionId;

    private String customerId;

    private String customerEmail;

    @Enumerated(EnumType.STRING)
    private VoiceSessionStatus status;

    private LocalDateTime createdAt;

    private LocalDateTime lastActivityAt;

    private LocalDateTime expiresAt;

    private Integer turnCount;

    private Long totalDuration;

    private Double successRate;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String metadata;
}
