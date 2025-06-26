package com.bistro_template_backend.models;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.LocalDateTime;

@Entity
@Table(name = "voice_analytics")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VoiceAnalytics {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_id", length = 255)
    private String sessionId;

    @Column(name = "customer_id", length = 50)
    private String customerId;

    @Column(name = "event_type", nullable = false, length = 50)
    private String eventType;

    @Column(name = "operation", length = 50)
    private String operation;

    @Column(name = "processing_time_ms")
    private Double processingTimeMs;

    @Column(name = "audio_duration_seconds")
    private Double audioDurationSeconds;

    @Column(name = "success", nullable = false)
    private Boolean success = true;

    @Column(name = "error_type", length = 100)
    private String errorType;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "api_cost")
    private Double apiCost;

    @Column(name = "token_count")
    private Integer tokenCount;

    @Column(name = "turn_number")
    private Integer turnNumber;

    @Column(name = "intent", length = 50)
    private String intent;

    @Column(name = "order_value")
    private Double orderValue;

    @Column(name = "item_count")
    private Integer itemCount;

    @Column(name = "created_order_id")
    private Long createdOrderId;

    @Column(name = "timestamp", nullable = false)
    private LocalDateTime timestamp;

    @Column(name = "date_only", nullable = false)
    private java.sql.Date dateOnly;

    // Additional metrics stored as JSON
    @Column(name = "metadata", columnDefinition = "TEXT")
    private String metadata;

    @PrePersist
    protected void onCreate() {
        if (timestamp == null) {
            timestamp = LocalDateTime.now();
        }
        if (dateOnly == null) {
            dateOnly = java.sql.Date.valueOf(timestamp.toLocalDate());
        }
    }
}