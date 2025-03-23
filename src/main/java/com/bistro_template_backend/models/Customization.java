package com.bistro_template_backend.models;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;

@Entity
@Table(name = "customizations")
@Data
public class Customization {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "menu_item_id", nullable = false)
    private MenuItem menuItem;

    private String name;
    private String type;  // "default" or "addon"
    private BigDecimal price;
    private Boolean isDefault;

    @Column(name = "choice_type")
    private String choiceType;  // 'single' or 'multiple'

    @Column(name = "group_name")
    private String groupName;  // e.g. "Main Option", "Drink Choice", etc.

    // Getters and Setters
}