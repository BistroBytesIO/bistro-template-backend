package com.bistro_template_backend.models;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "customer_accounts")
@Data
public class CustomerAccount {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "cognito_user_id", unique = true)
    private String cognitoUserId;

    @Column(name = "email", unique = true)
    private String email;

    @Column(name = "first_name")
    private String firstName;

    @Column(name = "last_name")
    private String lastName;

    @Column(name = "phone_number")
    private String phoneNumber;

    @Column(name = "birthday")
    private LocalDate birthday;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "last_login")
    private LocalDateTime lastLogin;

    @Column(name = "is_active")
    private Boolean isActive = true;

    // Rewards fields
    @Column(name = "total_reward_points")
    private Integer totalRewardPoints = 0;

    @Column(name = "available_reward_points")
    private Integer availableRewardPoints = 0;

    @Column(name = "lifetime_spent")
    private java.math.BigDecimal lifetimeSpent = java.math.BigDecimal.ZERO;

    @Column(name = "signup_bonus_awarded")
    private Boolean signupBonusAwarded = false;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}