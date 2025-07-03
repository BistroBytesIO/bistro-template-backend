package com.bistro_template_backend.models;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.LocalDateTime;

@Entity
@Table(name = "voice_sessions")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VoiceSession {

    @Id
    private String sessionId;

    @Column(name = "customer_id", nullable = false, length = 50)
    private String customerId;

    @Column(name = "customer_email", length = 255)
    private String customerEmail;

    @Column(name = "session_type", length = 50)
    private String sessionType;

    @Column(name = "connection_status", length = 50)
    private String connectionStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 50)
    private VoiceSessionStatus status;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "last_activity_at", nullable = false)
    private LocalDateTime lastActivityAt;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @Column(name = "ended_at")
    private LocalDateTime endedAt;

    @Column(name = "end_reason", length = 100)
    private String endReason;

    @Column(name = "turn_count", nullable = false)
    private Integer turnCount = 0;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Column(name = "language", length = 10)
    private String language = "en";

    @Column(name = "order_created")
    private Boolean orderCreated = false;

    @Column(name = "created_order_id")
    private Long createdOrderId;

    @Column(name = "total_duration")
    private Long totalDuration;

    @Column(name = "success_rate")
    private Double successRate;

    // Current order stored as JSON text
    @Column(name = "current_order", columnDefinition = "TEXT")
    private String currentOrder;

    // Session metadata stored as JSON text
    @Column(name = "metadata", columnDefinition = "TEXT")
    private String metadata;

    // Voice metrics stored as JSON text
    @Column(name = "voice_metrics", columnDefinition = "TEXT")
    private String voiceMetrics;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (lastActivityAt == null) {
            lastActivityAt = LocalDateTime.now();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        lastActivityAt = LocalDateTime.now();
    }
}
