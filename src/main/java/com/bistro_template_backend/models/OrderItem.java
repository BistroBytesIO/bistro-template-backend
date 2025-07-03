package com.bistro_template_backend.models;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Entity
@Table(name = "order_items")
@Data
@NoArgsConstructor
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // We'll store just the orderId here instead of a ManyToOne relationship.
    private Long orderId;

    // Similarly, store the menuItemId instead of referencing the MenuItem entity directly.
    private Long menuItemId;

    private int quantity;

    private BigDecimal itemPrice;
    // The price at the time of ordering, so changing menu price doesn’t retroactively change old orders.

    @Column(name = "is_reward_item")
    private boolean isRewardItem;

    @Column(name = "original_price")
    private BigDecimal originalPrice;

    @Column(name = "item_name", length = 255)
    private String itemName;

}