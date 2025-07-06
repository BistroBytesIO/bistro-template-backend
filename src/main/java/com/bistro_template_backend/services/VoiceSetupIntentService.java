package com.bistro_template_backend.services;

import com.bistro_template_backend.models.Customer;
import com.bistro_template_backend.repositories.CustomerRepository;
import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.SetupIntent;
import com.stripe.model.StripeError;
import com.stripe.param.SetupIntentCreateParams;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class VoiceSetupIntentService {

    private final CustomerRepository customerRepository;

    @Value("${stripe.secretKey}")
    private String stripeSecretKey;

    /**
     * Create a setup intent optimized for voice payment systems
     */
    public Map<String, Object> createVoiceOptimizedSetupIntent(String customerEmail, 
                                                               String paymentMethodType,
                                                               String deviceInfo) {
        try {
            Stripe.apiKey = stripeSecretKey;
            
            // Find or create customer
            Customer customer = findOrCreateCustomer(customerEmail);
            String stripeCustomerId = getOrCreateStripeCustomer(customer);
            
            // Create setup intent with voice-specific configuration
            SetupIntentCreateParams.Builder paramsBuilder = SetupIntentCreateParams.builder()
                    .setCustomer(stripeCustomerId)
                    .setUsage(SetupIntentCreateParams.Usage.OFF_SESSION)
                    .putMetadata("voice_system", "true")
                    .putMetadata("customer_email", customerEmail)
                    .putMetadata("payment_method_type", paymentMethodType)
                    .putMetadata("device_info", deviceInfo != null ? deviceInfo : "unknown")
                    .putMetadata("created_via", "voice_ordering");
            
            // Configure for specific payment method types
            if ("apple_pay".equals(paymentMethodType)) {
                paramsBuilder.addPaymentMethodType("card")
                           .setAutomaticPaymentMethods(
                               SetupIntentCreateParams.AutomaticPaymentMethods.builder()
                                   .setEnabled(true)
                                   .setAllowRedirects(SetupIntentCreateParams.AutomaticPaymentMethods.AllowRedirects.NEVER)
                                   .build()
                           );
            } else if ("google_pay".equals(paymentMethodType)) {
                paramsBuilder.addPaymentMethodType("card")
                           .setAutomaticPaymentMethods(
                               SetupIntentCreateParams.AutomaticPaymentMethods.builder()
                                   .setEnabled(true)
                                   .setAllowRedirects(SetupIntentCreateParams.AutomaticPaymentMethods.AllowRedirects.ALWAYS)
                                   .build()
                           );
            } else {
                // Default card setup
                paramsBuilder.addPaymentMethodType("card");
            }
            
            SetupIntent setupIntent = SetupIntent.create(paramsBuilder.build());
            
            // Return comprehensive response for voice interface
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("setupIntentId", setupIntent.getId());
            response.put("clientSecret", setupIntent.getClientSecret());
            response.put("status", setupIntent.getStatus());
            response.put("customerId", stripeCustomerId);
            response.put("paymentMethodType", paymentMethodType);
            
            // Add voice-specific instructions
            response.put("voiceInstructions", generateSetupInstructions(paymentMethodType));
            
            log.info("Created voice setup intent for customer: {} with type: {}", 
                    customerEmail, paymentMethodType);
            
            return response;
            
        } catch (StripeException e) {
            log.error("Error creating voice setup intent for customer: {}", customerEmail, e);
            return createErrorResponse("Failed to create setup intent: " + e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error creating setup intent: {}", e.getMessage(), e);
            return createErrorResponse("Setup intent creation failed");
        }
    }

    /**
     * Create setup intent specifically for Apple Pay
     */
    public Map<String, Object> createApplePaySetupIntent(String customerEmail) {
        return createVoiceOptimizedSetupIntent(customerEmail, "apple_pay", "iOS_device");
    }

    /**
     * Create setup intent specifically for Google Pay
     */
    public Map<String, Object> createGooglePaySetupIntent(String customerEmail) {
        return createVoiceOptimizedSetupIntent(customerEmail, "google_pay", "Android_device");
    }

    /**
     * Confirm setup intent completion and update customer record
     */
    public Map<String, Object> confirmSetupIntentCompletion(String setupIntentId, String customerEmail) {
        try {
            Stripe.apiKey = stripeSecretKey;
            
            SetupIntent setupIntent = SetupIntent.retrieve(setupIntentId);
            
            if ("succeeded".equals(setupIntent.getStatus())) {
                // Update customer with successful setup
                Optional<Customer> customerOpt = customerRepository.findByEmail(customerEmail);
                if (customerOpt.isPresent()) {
                    Customer customer = customerOpt.get();
                    customer.setLastOrderDate(LocalDateTime.now()); // Mark recent activity
                    customerRepository.save(customer);
                }
                
                Map<String, Object> response = new HashMap<>();
                response.put("success", true);
                response.put("status", "succeeded");
                response.put("paymentMethodId", setupIntent.getPaymentMethod());
                response.put("message", "Payment method successfully saved for voice ordering!");
                response.put("voiceConfirmation", "Great! Your payment method is now saved and ready for voice orders.");
                
                return response;
            } else {
                return createErrorResponse("Setup intent not yet completed");
            }
            
        } catch (StripeException e) {
            log.error("Error confirming setup intent: {}", setupIntentId, e);
            return createErrorResponse("Failed to confirm setup: " + e.getMessage());
        }
    }

    /**
     * Get setup intent status for voice interface
     */
    public Map<String, Object> getSetupIntentStatus(String setupIntentId) {
        try {
            Stripe.apiKey = stripeSecretKey;
            
            SetupIntent setupIntent = SetupIntent.retrieve(setupIntentId);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("status", setupIntent.getStatus());
            response.put("setupIntentId", setupIntent.getId());
            
            // Add voice-friendly status descriptions
            String voiceStatus = getVoiceStatusDescription(setupIntent.getStatus());
            response.put("voiceStatus", voiceStatus);
            
            if (setupIntent.getLastSetupError() != null) {
                response.put("error", setupIntent.getLastSetupError().getMessage());
                response.put("voiceError", getVoiceErrorDescription(setupIntent.getLastSetupError()));
            }
            
            return response;
            
        } catch (StripeException e) {
            log.error("Error retrieving setup intent status: {}", setupIntentId, e);
            return createErrorResponse("Failed to retrieve setup status: " + e.getMessage());
        }
    }

    // Helper methods

    private Customer findOrCreateCustomer(String customerEmail) {
        Optional<Customer> customerOpt = customerRepository.findByEmail(customerEmail);
        
        if (customerOpt.isPresent()) {
            return customerOpt.get();
        }
        
        // Create new customer
        Customer customer = new Customer();
        customer.setEmail(customerEmail);
        customer.setCreatedAt(LocalDateTime.now());
        customer.setTotalOrders(0);
        return customerRepository.save(customer);
    }

    private String getOrCreateStripeCustomer(Customer customer) throws StripeException {
        if (customer.getStripeCustomerId() != null) {
            return customer.getStripeCustomerId();
        }
        
        // Create Stripe customer
        Map<String, Object> params = new HashMap<>();
        params.put("email", customer.getEmail());
        if (customer.getFullName() != null) {
            params.put("name", customer.getFullName());
        }
        params.put("description", "Voice ordering customer");
        params.put("metadata", Map.of(
            "voice_customer", "true",
            "created_via", "voice_setup"
        ));
        
        com.stripe.model.Customer stripeCustomer = com.stripe.model.Customer.create(params);
        
        // Save Stripe customer ID
        customer.setStripeCustomerId(stripeCustomer.getId());
        customerRepository.save(customer);
        
        return stripeCustomer.getId();
    }

    private List<String> generateSetupInstructions(String paymentMethodType) {
        List<String> instructions = new ArrayList<>();
        
        switch (paymentMethodType) {
            case "apple_pay":
                instructions.add("You'll be prompted to use Touch ID or Face ID to save your Apple Pay method.");
                instructions.add("Once saved, you can pay for voice orders by just saying 'pay with Apple Pay'.");
                break;
                
            case "google_pay":
                instructions.add("You'll be prompted to use your fingerprint or PIN to save your Google Pay method.");
                instructions.add("Once saved, you can pay for voice orders by just saying 'pay with Google Pay'.");
                break;
                
            default:
                instructions.add("You'll be prompted to enter your card details securely.");
                instructions.add("Once saved, you can pay for voice orders by saying 'use my saved card'.");
                break;
        }
        
        return instructions;
    }

    private String getVoiceStatusDescription(String status) {
        switch (status) {
            case "requires_payment_method":
                return "Waiting for payment method setup";
            case "requires_confirmation":
                return "Please confirm your payment method";
            case "requires_action":
                return "Authentication required - please complete verification";
            case "processing":
                return "Setting up your payment method";
            case "succeeded":
                return "Payment method successfully saved!";
            case "canceled":
                return "Setup was canceled";
            default:
                return "Unknown status: " + status;
        }
    }

    private String getVoiceErrorDescription(StripeError error) {
        if (error == null) return "Unknown error occurred";
        
        switch (error.getCode()) {
            case "card_declined":
                return "Your card was declined. Please try a different payment method.";
            case "expired_card":
                return "Your card has expired. Please use a current card.";
            case "incorrect_cvc":
                return "The security code is incorrect. Please check and try again.";
            case "processing_error":
                return "There was a processing error. Please try again.";
            default:
                return "Setup failed: " + error.getMessage();
        }
    }

    private Map<String, Object> createErrorResponse(String message) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("error", message);
        return response;
    }
}