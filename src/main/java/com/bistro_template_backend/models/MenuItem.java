package com.bistro_template_backend.models;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Entity
@Table(name = "menu_items")
@Data
@NoArgsConstructor
public class MenuItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;
    @Column(columnDefinition = "TEXT")
    private String description;
    private BigDecimal price;
    private String category;
    private String imageUrl;
    private Boolean isAvailable = true;
    private Integer stockQuantity;
    private Boolean complexItem;

    @Column(name = "category_id")
    private Long categoryId;

    @Column(name = "is_featured")
    private Boolean isFeatured;

    @Column(name = "is_reward_item")
    private Boolean isRewardItem = false;

    @Column(name = "points_to_redeem")
    private Integer pointsToRedeem;

}
