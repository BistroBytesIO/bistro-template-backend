package com.bistro_template_backend.controllers;

import com.bistro_template_backend.services.SavedPaymentMethodService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/profile/payments")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
public class SavedPaymentMethodController {

    private final SavedPaymentMethodService savedPaymentMethodService;

    /**
     * Get all saved payment methods for a customer
     */
    @GetMapping("/methods")
    public ResponseEntity<Map<String, Object>> getSavedPaymentMethods(
            @RequestParam String customerEmail) {
        
        log.info("Getting saved payment methods for customer: {}", customerEmail);
        
        Map<String, Object> response = savedPaymentMethodService.getSavedPaymentMethods(customerEmail);
        return ResponseEntity.ok(response);
    }

    /**
     * Create setup intent for saving a new payment method
     */
    @PostMapping("/setup-intent")
    public ResponseEntity<Map<String, Object>> createSetupIntent(
            @RequestBody Map<String, String> request) {
        
        String customerEmail = request.get("customerEmail");
        String paymentMethodType = request.getOrDefault("paymentMethodType", "card");
        
        log.info("Creating setup intent for customer: {} with type: {}", customerEmail, paymentMethodType);
        
        Map<String, Object> response = savedPaymentMethodService.createSetupIntent(customerEmail, paymentMethodType);
        return ResponseEntity.ok(response);
    }

    /**
     * Remove a saved payment method
     */
    @DeleteMapping("/methods/{paymentMethodId}")
    public ResponseEntity<Map<String, Object>> removePaymentMethod(
            @PathVariable String paymentMethodId,
            @RequestBody Map<String, String> request) {
        
        String customerEmail = request.get("customerEmail");
        
        log.info("Removing payment method {} for customer: {}", paymentMethodId, customerEmail);
        
        Map<String, Object> response = savedPaymentMethodService.removePaymentMethod(paymentMethodId, customerEmail);
        return ResponseEntity.ok(response);
    }

    /**
     * Get specific payment method details for voice ordering
     */
    @GetMapping("/methods/{paymentMethodId}")
    public ResponseEntity<Map<String, Object>> getPaymentMethodDetails(
            @PathVariable String paymentMethodId,
            @RequestParam String customerEmail) {
        
        log.info("Getting payment method {} details for customer: {}", paymentMethodId, customerEmail);
        
        Map<String, Object> response = savedPaymentMethodService.getPaymentMethodForVoice(paymentMethodId, customerEmail);
        return ResponseEntity.ok(response);
    }

    /**
     * Check if customer has any saved payment methods (for voice ordering eligibility)
     */
    @GetMapping("/check")
    public ResponseEntity<Map<String, Object>> checkPaymentMethodsAvailability(
            @RequestParam String customerEmail) {
        
        log.info("Checking payment methods availability for customer: {}", customerEmail);
        
        Map<String, Object> paymentMethodsResponse = savedPaymentMethodService.getSavedPaymentMethods(customerEmail);
        
        // Extract just the availability information
        Map<String, Object> response = Map.of(
            "success", paymentMethodsResponse.get("success"),
            "hasPaymentMethods", 
                paymentMethodsResponse.get("success").equals(true) && 
                !((java.util.List<?>) paymentMethodsResponse.get("paymentMethods")).isEmpty(),
            "count", 
                paymentMethodsResponse.get("success").equals(true) ? 
                    ((java.util.List<?>) paymentMethodsResponse.get("paymentMethods")).size() : 0,
            "capabilities", paymentMethodsResponse.get("capabilities")
        );
        
        return ResponseEntity.ok(response);
    }
}