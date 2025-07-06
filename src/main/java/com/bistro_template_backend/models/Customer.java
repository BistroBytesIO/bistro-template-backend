// src/main/java/com/bistro_template_backend/models/Customer.java

package com.bistro_template_backend.models;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "customers")
@Data
@NoArgsConstructor
public class Customer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "full_name")
    private String fullName;

    @Column(unique = true)
    private String email;

    private String phone;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "last_order_date")
    private LocalDateTime lastOrderDate;

    @Column(name = "total_orders")
    private Integer totalOrders = 0;

    @Column(name = "stripe_customer_id")
    private String stripeCustomerId;
}