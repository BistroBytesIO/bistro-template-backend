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

    @Column(name = "customer_email", nullable = false, length = 255)
    private String customerEmail;

    @Column(name = "customer_name", length = 100)
    private String customerName;

    @Column(name = "customer_phone", length = 20)
    private String customerPhone;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "last_activity_at", nullable = false)
    private LocalDateTime lastActivityAt;

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

    // Conversation history stored as JSON text
    @Column(name = "conversation_history", columnDefinition = "TEXT")
    private String conversationHistory;

    // Current order stored as JSON text
    @Column(name = "current_order", columnDefinition = "TEXT")
    private String currentOrder;

    // Session metadata stored as JSON text
    @Column(name = "metadata", columnDefinition = "TEXT")
    private String metadata;

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