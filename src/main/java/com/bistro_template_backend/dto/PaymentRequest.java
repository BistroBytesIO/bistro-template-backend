package com.bistro_template_backend.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
public class PaymentRequest {
    private String paymentMethod;  // e.g. "STRIPE", "PAYPAL"
    private BigDecimal amount;     // total to be charged
    private String currency;       // e.g. "USD"
    private String description;    // "Order #123"
    // ...any other fields (customer email, etc.)

}