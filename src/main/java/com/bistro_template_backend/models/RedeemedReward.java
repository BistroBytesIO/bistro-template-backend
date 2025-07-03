package com.bistro_template_backend.models;

import jakarta.persistence.*;
import lombok.Data;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "redeemed_rewards")
@Data
@EntityListeners(AuditingEntityListener.class)
public class RedeemedReward {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "customer_account_id", nullable = false)
    private Long customerAccountId;

    @Column(name = "menu_item_id", nullable = false)
    private Long menuItemId;

    @Column(name = "quantity_available", nullable = false)
    private Integer quantityAvailable = 1;

    @Column(name = "points_redeemed", nullable = false)
    private Integer pointsRedeemed;

    @CreatedDate
    @Column(name = "date_redeemed", nullable = false, updatable = false)
    private LocalDateTime dateRedeemed;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_account_id", insertable = false, updatable = false)
    private CustomerAccount customerAccount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "menu_item_id", insertable = false, updatable = false)
    private MenuItem menuItem;

    @PrePersist
    protected void onCreate() {
        dateRedeemed = LocalDateTime.now();
    }
}