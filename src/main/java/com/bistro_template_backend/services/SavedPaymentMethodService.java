package com.bistro_template_backend.services;

import com.bistro_template_backend.models.Customer;
import com.bistro_template_backend.repositories.CustomerRepository;
import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentMethod;
import com.stripe.model.SetupIntent;
import com.stripe.param.PaymentMethodListParams;
import com.stripe.param.SetupIntentCreateParams;
import com.stripe.model.checkout.Session;
import com.stripe.param.checkout.SessionCreateParams;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class SavedPaymentMethodService {

    private final CustomerRepository customerRepository;

    @Value("${stripe.secretKey}")
    private String stripeSecretKey;

    /**
     * Get all saved payment methods for a customer
     */
    public Map<String, Object> getSavedPaymentMethods(String customerEmail) {
        try {
            Stripe.apiKey = stripeSecretKey;
            
            log.info("Looking up customer by email: {}", customerEmail);
            Customer customer = findCustomerByEmail(customerEmail);
            
            if (customer == null) {
                log.warn("Customer not found in database for email: {}", customerEmail);
                return createSuccessResponse(Collections.emptyList(), false, false);
            }
            
            log.info("Customer found: {} with Stripe ID: {}", customer.getEmail(), customer.getStripeCustomerId());
            
            if (customer.getStripeCustomerId() == null) {
                log.warn("Customer exists but has no Stripe customer ID: {}", customerEmail);
                return createSuccessResponse(Collections.emptyList(), false, false);
            }

            // Retrieve payment methods from Stripe
            log.info("Retrieving payment methods from Stripe for customer: {}", customer.getStripeCustomerId());
            PaymentMethodListParams params = PaymentMethodListParams.builder()
                    .setCustomer(customer.getStripeCustomerId())
                    .setType(PaymentMethodListParams.Type.CARD)
                    .build();

            var paymentMethods = PaymentMethod.list(params);
            log.info("Stripe returned {} payment methods", paymentMethods.getData().size());
            
            List<Map<String, Object>> paymentMethodList = new ArrayList<>();
            boolean hasApplePay = false;
            boolean hasGooglePay = false;

            for (PaymentMethod pm : paymentMethods.getData()) {
                Map<String, Object> methodData = new HashMap<>();
                methodData.put("id", pm.getId());
                methodData.put("type", pm.getType());
                
                if ("card".equals(pm.getType()) && pm.getCard() != null) {
                    var card = pm.getCard();
                    
                    // Check if it's a digital wallet (only Apple Pay and Google Pay)
                    // Link and other wallet types are treated as regular cards for voice ordering
                    boolean isDigitalWallet = card.getWallet() != null && 
                                            ("apple_pay".equals(card.getWallet().getType()) ||
                                             "google_pay".equals(card.getWallet().getType()));
                    
                    methodData.put("isDigitalWallet", isDigitalWallet);
                    methodData.put("brand", card.getBrand());
                    methodData.put("last4", card.getLast4());
                    methodData.put("expiryMonth", card.getExpMonth());
                    methodData.put("expiryYear", card.getExpYear());
                    
                    if (isDigitalWallet && card.getWallet() != null) {
                        String walletType = card.getWallet().getType();
                        methodData.put("walletType", walletType);
                        
                        if ("apple_pay".equals(walletType)) {
                            hasApplePay = true;
                            methodData.put("voiceDescription", "Apple Pay");
                        } else if ("google_pay".equals(walletType)) {
                            hasGooglePay = true;
                            methodData.put("voiceDescription", "Google Pay");
                        }
                    } else {
                        // Regular card (including Link and other non-digital wallet types)
                        String cardDescription = card.getBrand().substring(0, 1).toUpperCase() + 
                            card.getBrand().substring(1) + " ending in " + card.getLast4();
                        
                        // Add Link indicator if it's a Link payment method
                        if (card.getWallet() != null && "link".equals(card.getWallet().getType())) {
                            cardDescription += " (Link)";
                        }
                        
                        methodData.put("voiceDescription", cardDescription);
                    }
                    
                    // Check if this is the customer's default payment method
                    methodData.put("isDefault", false); // We can implement default logic later
                    
                    paymentMethodList.add(methodData);
                }
            }

            return createSuccessResponse(paymentMethodList, hasApplePay, hasGooglePay);
            
        } catch (StripeException e) {
            log.error("Stripe error retrieving payment methods for customer: {} - Code: {}, Message: {}", 
                customerEmail, e.getCode(), e.getMessage(), e);
            return createErrorResponse("Failed to retrieve payment methods: " + e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error retrieving payment methods: {}", e.getMessage(), e);
            return createErrorResponse("Failed to retrieve payment methods");
        }
    }

    /**
     * Create setup intent for saving a new payment method
     */
    public Map<String, Object> createSetupIntent(String customerEmail, String paymentMethodType) {
        try {
            Stripe.apiKey = stripeSecretKey;
            
            Customer customer = findOrCreateCustomer(customerEmail);
            String stripeCustomerId = getOrCreateStripeCustomer(customer);
            
            // Create Checkout Session for card setup (modern approach)
            if ("card".equals(paymentMethodType)) {
                return createCheckoutSessionForSetup(stripeCustomerId, customerEmail);
            }
            
            // For digital wallets, still use SetupIntent approach
            SetupIntentCreateParams.Builder paramsBuilder = SetupIntentCreateParams.builder()
                    .setCustomer(stripeCustomerId)
                    .setUsage(SetupIntentCreateParams.Usage.OFF_SESSION)
                    .putMetadata("customer_email", customerEmail)
                    .putMetadata("payment_method_type", paymentMethodType)
                    .putMetadata("created_via", "profile_management");

            // Configure based on payment method type
            switch (paymentMethodType) {
                case "apple_pay":
                    // For Apple Pay, use Checkout Session which is more reliable
                    return createCheckoutSessionForSetup(stripeCustomerId, customerEmail, "apple_pay");
                case "google_pay":
                    // For Google Pay, use SetupIntent to work with Stripe Elements PaymentRequest
                    paramsBuilder.addPaymentMethodType("card");
                    break;
                default:
                    paramsBuilder.addPaymentMethodType("card");
                    break;
            }

            SetupIntent setupIntent = SetupIntent.create(paramsBuilder.build());
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("setupIntentId", setupIntent.getId());
            response.put("clientSecret", setupIntent.getClientSecret());
            response.put("customerId", stripeCustomerId);
            
            log.info("Created setup intent for customer: {} with type: {}", customerEmail, paymentMethodType);
            return response;
            
        } catch (StripeException e) {
            log.error("Error creating setup intent for customer: {}", customerEmail, e);
            return createErrorResponse("Failed to create setup intent: " + e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error creating setup intent: {}", e.getMessage(), e);
            return createErrorResponse("Setup intent creation failed");
        }
    }

    /**
     * Create Checkout Session for card setup (modern approach)
     */
    private Map<String, Object> createCheckoutSessionForSetup(String stripeCustomerId, String customerEmail) {
        return createCheckoutSessionForSetup(stripeCustomerId, customerEmail, "card");
    }
    
    /**
     * Create Checkout Session for payment method setup with specific type
     */
    private Map<String, Object> createCheckoutSessionForSetup(String stripeCustomerId, String customerEmail, String paymentMethodType) {
        try {
            SessionCreateParams.Builder paramsBuilder = SessionCreateParams.builder()
                    .setMode(SessionCreateParams.Mode.SETUP)
                    .setCustomer(stripeCustomerId)
                    .setSuccessUrl("http://localhost:3000/profile?setup=success")
                    .setCancelUrl("http://localhost:3000/profile?setup=cancelled")
                    .putMetadata("customer_email", customerEmail)
                    .putMetadata("purpose", "save_payment_method_for_voice_ordering")
                    .putMetadata("payment_method_type", paymentMethodType);

            // Configure payment method types based on the request
            if ("google_pay".equals(paymentMethodType)) {
                // The issue is that Google Pay setup requires a different approach
                // We need to use the payment method type that explicitly supports Google Pay
                // However, for setup mode, we may need to use the Payment Request API directly
                
                // Try using Link payment method type which may work better for wallet setup
                paramsBuilder.addPaymentMethodType(SessionCreateParams.PaymentMethodType.LINK);
                paramsBuilder.addPaymentMethodType(SessionCreateParams.PaymentMethodType.CARD);
                
                // Configure payment method options for setup
                paramsBuilder.setPaymentMethodOptions(
                    SessionCreateParams.PaymentMethodOptions.builder()
                        .setCard(SessionCreateParams.PaymentMethodOptions.Card.builder()
                            .setSetupFutureUsage(SessionCreateParams.PaymentMethodOptions.Card.SetupFutureUsage.OFF_SESSION)
                            .build())
                        .build()
                );
                
                log.info("Configuring session for Google Pay setup with Link support");
            } else if ("apple_pay".equals(paymentMethodType)) {
                // For Apple Pay, use similar approach
                paramsBuilder.addPaymentMethodType(SessionCreateParams.PaymentMethodType.CARD);
                
                paramsBuilder.setPaymentMethodOptions(
                    SessionCreateParams.PaymentMethodOptions.builder()
                        .setCard(SessionCreateParams.PaymentMethodOptions.Card.builder()
                            .setSetupFutureUsage(SessionCreateParams.PaymentMethodOptions.Card.SetupFutureUsage.OFF_SESSION)
                            .build())
                        .build()
                );
                
                log.info("Configuring session specifically for Apple Pay setup");
            } else {
                // For regular cards, use standard configuration
                paramsBuilder.addPaymentMethodType(SessionCreateParams.PaymentMethodType.CARD);
            }

            Session session = Session.create(paramsBuilder.build());
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("sessionId", session.getId());
            response.put("sessionUrl", session.getUrl());
            response.put("customerId", stripeCustomerId);
            
            log.info("Created Checkout Session for {} setup - customer: {} session: {}", paymentMethodType, customerEmail, session.getId());
            return response;
            
        } catch (StripeException e) {
            log.error("Error creating Checkout Session for {} setup: {}", paymentMethodType, e.getMessage(), e);
            return createErrorResponse("Failed to create checkout session: " + e.getMessage());
        }
    }

    /**
     * Remove a saved payment method
     */
    public Map<String, Object> removePaymentMethod(String paymentMethodId, String customerEmail) {
        try {
            Stripe.apiKey = stripeSecretKey;
            
            // Verify the payment method belongs to the customer
            Customer customer = findCustomerByEmail(customerEmail);
            if (customer == null || customer.getStripeCustomerId() == null) {
                return createErrorResponse("Customer not found");
            }

            PaymentMethod paymentMethod = PaymentMethod.retrieve(paymentMethodId);
            
            if (!customer.getStripeCustomerId().equals(paymentMethod.getCustomer())) {
                return createErrorResponse("Payment method does not belong to this customer");
            }

            // Detach the payment method
            paymentMethod.detach();
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Payment method removed successfully");
            
            log.info("Removed payment method {} for customer: {}", paymentMethodId, customerEmail);
            return response;
            
        } catch (StripeException e) {
            log.error("Error removing payment method {} for customer: {}", paymentMethodId, customerEmail, e);
            return createErrorResponse("Failed to remove payment method: " + e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error removing payment method: {}", e.getMessage(), e);
            return createErrorResponse("Failed to remove payment method");
        }
    }

    /**
     * Get specific payment method for voice ordering
     */
    public Map<String, Object> getPaymentMethodForVoice(String paymentMethodId, String customerEmail) {
        try {
            Stripe.apiKey = stripeSecretKey;
            
            Customer customer = findCustomerByEmail(customerEmail);
            if (customer == null || customer.getStripeCustomerId() == null) {
                return createErrorResponse("Customer not found");
            }

            PaymentMethod paymentMethod = PaymentMethod.retrieve(paymentMethodId);
            
            if (!customer.getStripeCustomerId().equals(paymentMethod.getCustomer())) {
                return createErrorResponse("Payment method does not belong to this customer");
            }

            Map<String, Object> methodData = new HashMap<>();
            methodData.put("id", paymentMethod.getId());
            methodData.put("ready_for_voice", true);
            
            if ("card".equals(paymentMethod.getType()) && paymentMethod.getCard() != null) {
                var card = paymentMethod.getCard();
                methodData.put("brand", card.getBrand());
                methodData.put("last4", card.getLast4());
                
                // Voice description
                if (card.getWallet() != null) {
                    String walletType = card.getWallet().getType();
                    if ("apple_pay".equals(walletType)) {
                        methodData.put("voice_description", "Apple Pay");
                    } else if ("google_pay".equals(walletType)) {
                        methodData.put("voice_description", "Google Pay");
                    }
                } else {
                    methodData.put("voice_description", 
                        card.getBrand().substring(0, 1).toUpperCase() + 
                        card.getBrand().substring(1) + " ending in " + card.getLast4());
                }
            }

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("paymentMethod", methodData);
            
            return response;
            
        } catch (StripeException e) {
            log.error("Error retrieving payment method {} for customer: {}", paymentMethodId, customerEmail, e);
            return createErrorResponse("Failed to retrieve payment method: " + e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error retrieving payment method: {}", e.getMessage(), e);
            return createErrorResponse("Failed to retrieve payment method");
        }
    }

    // Helper methods
    
    private Customer findCustomerByEmail(String customerEmail) {
        return customerRepository.findByEmail(customerEmail).orElse(null);
    }

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
        params.put("description", "Saved payment methods customer");
        params.put("metadata", Map.of(
            "profile_customer", "true",
            "created_via", "payment_setup"
        ));
        
        com.stripe.model.Customer stripeCustomer = com.stripe.model.Customer.create(params);
        
        // Save Stripe customer ID
        customer.setStripeCustomerId(stripeCustomer.getId());
        customerRepository.save(customer);
        
        return stripeCustomer.getId();
    }

    private Map<String, Object> createSuccessResponse(List<Map<String, Object>> paymentMethods, 
                                                     boolean hasApplePay, boolean hasGooglePay) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("paymentMethods", paymentMethods);
        
        Map<String, Object> capabilities = new HashMap<>();
        capabilities.put("hasApplePay", hasApplePay);
        capabilities.put("hasGooglePay", hasGooglePay);
        capabilities.put("hasCards", paymentMethods.stream()
            .anyMatch(pm -> !(Boolean) pm.getOrDefault("isDigitalWallet", false)));
        
        response.put("capabilities", capabilities);
        return response;
    }

    private Map<String, Object> createErrorResponse(String message) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("error", message);
        response.put("paymentMethods", Collections.emptyList());
        
        Map<String, Object> capabilities = new HashMap<>();
        capabilities.put("hasApplePay", false);
        capabilities.put("hasGooglePay", false);
        capabilities.put("hasCards", false);
        
        response.put("capabilities", capabilities);
        return response;
    }
}