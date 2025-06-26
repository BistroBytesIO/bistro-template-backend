package com.bistro_template_backend.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import jakarta.validation.constraints.*;
import jakarta.validation.Valid;

import java.math.BigDecimal;
import java.util.List;

@Data
@NoArgsConstructor
public class CreateOrderRequest {
    private Long customerId; // optional
    
    @NotNull(message = "Items list is required")
    @NotEmpty(message = "At least one item is required")
    @Valid
    private List<CartItemDTO> items;
    
    @NotBlank(message = "Customer name is required")
    @Size(max = 100, message = "Customer name must not exceed 100 characters")
    private String customerName;
    
    @NotBlank(message = "Customer email is required")
    @Email(message = "Customer email should be valid")
    private String customerEmail;
    
    @Pattern(regexp = "^$|^\\+?[1-9]\\d{1,14}$", message = "Customer phone should be valid")
    private String customerPhone;
    
    @Size(max = 500, message = "Special notes must not exceed 500 characters")
    private String specialNotes;

    @Data
    public static class CartItemDTO {
        @NotNull(message = "Menu item ID is required")
        private Long menuItemId;
        
        @Min(value = 1, message = "Quantity must be at least 1")
        @Max(value = 99, message = "Quantity must not exceed 99")
        private int quantity;
        
        @NotNull(message = "Price is required")
        @DecimalMin(value = "0.00", message = "Price must not be negative") // Changed from 0.01
        @DecimalMax(value = "9999.99", message = "Price must not exceed 9999.99")
        private BigDecimal priceAtOrderTime;

        private boolean isRewardItem;

        private BigDecimal originalPrice;

        private String name; // Item name for preservation

        @Valid
        private List<CustomizationDTO> customizations;

        // Getters and Setters

        @Data
        public static class CustomizationDTO {
            @NotNull(message = "Customization ID is required")
            private Long id; // Customization ID
        }
    }
}
