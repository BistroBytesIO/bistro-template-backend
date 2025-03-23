package com.bistro_template_backend.models;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "orders")
@Data
@NoArgsConstructor
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private LocalDateTime orderDate;

    @Enumerated(EnumType.STRING)
    private OrderStatus status; // e.g., PENDING, COMPLETED, CANCELED
    private BigDecimal subTotal;    // sum of itemPrice * quantity
    private BigDecimal tax;
    private BigDecimal serviceFee; // or convenienceFee, if applicable
    private BigDecimal discount;    // a negative value applied to reduce total
    private BigDecimal totalAmount;

    @Enumerated(EnumType.STRING)
    private PaymentStatus paymentStatus; // e.g., NOT_PAID, PAID, REFUNDED, FAILED

    // (Optional) If you want to store the customer's ID only (instead of a @ManyToOne)
    private Long customerId;
    private String customerName;
    private String customerEmail;

    private String specialNotes;

    // No @OneToMany to OrderItem. We'll store orderId in OrderItem separately.

}
