package com.bistro_template_backend.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.util.List;

@Data
@NoArgsConstructor
public class PaymentConfirmationRequest {
    
    private String name;
    private String email;
    private String phone;
    
    @Valid
    private List<PaymentItemDTO> items;

    @Data
    @NoArgsConstructor
    public static class PaymentItemDTO {
        @NotNull(message = "Menu item ID is required")
        private Long menuItemId;
        
        @Min(value = 1, message = "Quantity must be at least 1")
        private int quantity;
        
        @NotNull(message = "Price is required")
        @DecimalMin(value = "0.00", message = "Price must not be negative")
        private BigDecimal price;
        
        private BigDecimal originalPrice;
        
        private boolean isRewardItem;
        
        private String name; // Item name
        
        @Valid
        private List<CustomizationDTO> customizations;

        @Data
        @NoArgsConstructor
        public static class CustomizationDTO {
            @NotNull(message = "Customization ID is required")
            private Long id;
            private String name;
            private BigDecimal price;
        }
    }
}