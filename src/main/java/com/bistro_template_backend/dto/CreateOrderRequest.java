package com.bistro_template_backend.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@NoArgsConstructor
public class CreateOrderRequest {
    private Long customerId; // optional
    private List<CartItemDTO> items;
    private String customerName;
    private String customerEmail;
    private String specialNotes;

    @Data
    public static class CartItemDTO {
        private Long menuItemId;
        private int quantity;
        private BigDecimal priceAtOrderTime;
        private List<CustomizationDTO> customizations;

        // Getters and Setters

        @Data
        public static class CustomizationDTO {
            private Long id; // Customization ID
        }
    }
}
