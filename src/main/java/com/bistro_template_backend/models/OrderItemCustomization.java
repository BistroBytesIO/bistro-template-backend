package com.bistro_template_backend.models;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "order_item_customizations")
@Data
public class OrderItemCustomization {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "order_item_id", nullable = false)
    private OrderItem orderItem;

    @ManyToOne
    @JoinColumn(name = "customization_id", nullable = false)
    private Customization customization;

    // Getters and Setters
}
