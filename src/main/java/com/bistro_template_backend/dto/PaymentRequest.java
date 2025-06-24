package com.bistro_template_backend.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
public class PaymentRequest {
    @NotBlank(message = "Payment method is required")
    @Pattern(regexp = "^(STRIPE|APPLE_PAY|GOOGLE_PAY|express_checkout)$", message = "Invalid payment method")
    private String paymentMethod;  // e.g. "STRIPE", "PAYPAL"
    
    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.50", message = "Amount must be at least $0.50")
    @DecimalMax(value = "99999.99", message = "Amount must not exceed $99,999.99")
    private BigDecimal amount;     // total to be charged
    
    @NotBlank(message = "Currency is required")
    @Pattern(regexp = "^[A-Z]{3}$", message = "Currency must be a valid 3-letter code")
    private String currency;       // e.g. "USD"
    
    @Size(max = 255, message = "Description must not exceed 255 characters")
    private String description;    // "Order #123"
    // ...any other fields (customer email, etc.)

}