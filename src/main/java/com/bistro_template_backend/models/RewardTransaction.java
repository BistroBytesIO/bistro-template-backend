package com.bistro_template_backend.models;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "reward_transactions")
@Data
public class RewardTransaction {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "customer_account_id")
    private Long customerAccountId;

    @Column(name = "order_id")
    private Long orderId;

    @Column(name = "transaction_type")
    private String transactionType; // EARNED, REDEEMED, EXPIRED, BONUS

    @Column(name = "points_amount")
    private Integer pointsAmount;

    @Column(name = "dollar_amount")
    private BigDecimal dollarAmount;

    @Column(name = "description")
    private String description;

    @Column(name = "reference_id")
    private String referenceId; // For tracking redemptions

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}