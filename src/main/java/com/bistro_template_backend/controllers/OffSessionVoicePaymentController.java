package com.bistro_template_backend.controllers;

import com.bistro_template_backend.services.OffSessionVoicePaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/voice/payments/off-session")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
public class OffSessionVoicePaymentController {

    private final OffSessionVoicePaymentService offSessionPaymentService;

    /**
     * Process off-session payment for voice orders
     */
    @PostMapping("/process")
    public ResponseEntity<Map<String, Object>> processOffSessionPayment(
            @RequestBody Map<String, Object> request) {
        
        String customerEmail = (String) request.get("customerEmail");
        Long orderId = Long.valueOf(request.get("orderId").toString());
        String paymentMethodType = (String) request.get("paymentMethodType");
        
        log.info("Processing off-session payment for order {} using {} for customer {}", 
                orderId, paymentMethodType, customerEmail);
        
        Map<String, Object> response = offSessionPaymentService.processOffSessionPayment(
                customerEmail, orderId, paymentMethodType);
        
        return ResponseEntity.ok(response);
    }

    /**
     * Get voice payment options for a customer
     */
    @GetMapping("/options")
    public ResponseEntity<Map<String, Object>> getVoicePaymentOptions(
            @RequestParam String customerEmail) {
        
        log.info("Getting voice payment options for customer: {}", customerEmail);
        
        Map<String, Object> response = offSessionPaymentService.getVoicePaymentOptions(customerEmail);
        return ResponseEntity.ok(response);
    }

    /**
     * Validate off-session payment eligibility by payment method type
     */
    @PostMapping("/validate-by-type")
    public ResponseEntity<Map<String, Object>> validateOffSessionEligibility(
            @RequestBody Map<String, String> request) {
        
        String customerEmail = request.get("customerEmail");
        String paymentMethodType = request.get("paymentMethodType");
        
        log.info("Validating off-session eligibility for customer {} with payment type {}", 
                customerEmail, paymentMethodType);
        
        Map<String, Object> response = offSessionPaymentService.validateOffSessionEligibility(
                customerEmail, paymentMethodType);
        
        return ResponseEntity.ok(response);
    }
}