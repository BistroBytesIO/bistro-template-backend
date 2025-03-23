package com.bistro_template_backend.models;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "payments")
@Data
@NoArgsConstructor
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Just store the orderId
    private Long orderId;

    private String paymentMethod;  // e.g. "CARD", "PAYPAL", "STRIPE"

    private BigDecimal amount;     // The amount charged

    private String transactionId;  // e.g., Stripe or PayPal transaction reference

    @Enumerated(EnumType.STRING)
    private PaymentStatus status;  // e.g., INITIATED, COMPLETED, FAILED

    private LocalDateTime createdAt;

    @PrePersist
    public void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
    }

}