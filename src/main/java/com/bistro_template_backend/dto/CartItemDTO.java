package com.bistro_template_backend.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
public class CartItemDTO {
    private Long menuItemId;
    private int quantity;
    private BigDecimal priceAtOrderTime;
    // Optional: you can also let the server look up the menu item’s price

}
