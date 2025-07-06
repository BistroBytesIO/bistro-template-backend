package com.bistro_template_backend.services;

import com.bistro_template_backend.models.Customer;
import com.bistro_template_backend.models.Order;
import com.bistro_template_backend.models.Payment;
import com.bistro_template_backend.models.PaymentStatus;
import com.bistro_template_backend.repositories.CustomerRepository;
import com.bistro_template_backend.repositories.OrderRepository;
import com.bistro_template_backend.repositories.PaymentRepository;
import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.stripe.model.PaymentMethod;
import com.stripe.model.SetupIntent;
import com.stripe.param.PaymentIntentCreateParams;
import com.stripe.param.PaymentMethodListParams;
import com.stripe.param.SetupIntentCreateParams;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class VoicePaymentService {

    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;
    private final CustomerRepository customerRepository;
    private final PaymentService paymentService;

    @Value("${stripe.secretKey}")
    private String stripeSecretKey;

    /**
     * Get all saved payment methods for a customer
     */
    public Map<String, Object> getCustomerPaymentMethods(String customerEmail) {
        try {
            Stripe.apiKey = stripeSecretKey;
            
            // Find customer by email
            Optional<Customer> customerOpt = customerRepository.findByEmail(customerEmail);
            if (customerOpt.isEmpty()) {
                return createErrorResponse("Customer not found");
            }
            
            Customer customer = customerOpt.get();
            
            // Get Stripe customer ID (we'll need to add this to Customer entity)
            String stripeCustomerId = customer.getStripeCustomerId();
            if (stripeCustomerId == null) {
                return createSuccessResponse(new ArrayList<>(), new HashMap<>());
            }
            
            // Retrieve payment methods from Stripe
            PaymentMethodListParams params = PaymentMethodListParams.builder()
                    .setCustomer(stripeCustomerId)
                    .setType(PaymentMethodListParams.Type.CARD)
                    .setLimit(10L)
                    .build();
            
            List<PaymentMethod> paymentMethods = PaymentMethod.list(params).getData();
            
            // Process payment methods for voice interface
            List<Map<String, Object>> voicePaymentMethods = paymentMethods.stream()
                    .map(this::formatPaymentMethodForVoice)
                    .collect(Collectors.toList());
            
            // Detect digital wallet capabilities
            Map<String, Object> walletCapabilities = detectWalletCapabilities(paymentMethods);
            
            return createSuccessResponse(voicePaymentMethods, walletCapabilities);
            
        } catch (StripeException e) {
            log.error("Error retrieving payment methods for customer: {}", customerEmail, e);
            return createErrorResponse("Failed to retrieve payment methods: " + e.getMessage());
        }
    }

    /**
     * Create a setup intent for storing payment methods off-session
     */
    public Map<String, Object> createSetupIntent(String customerEmail) {
        try {
            Stripe.apiKey = stripeSecretKey;
            
            // Find or create customer
            Customer customer = findOrCreateCustomer(customerEmail);
            String stripeCustomerId = getOrCreateStripeCustomer(customer);
            
            SetupIntentCreateParams params = SetupIntentCreateParams.builder()
                    .setCustomer(stripeCustomerId)
                    .setUsage(SetupIntentCreateParams.Usage.OFF_SESSION)
                    .putMetadata("voice_system", "true")
                    .putMetadata("customer_email", customerEmail)
                    .build();
            
            SetupIntent setupIntent = SetupIntent.create(params);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("clientSecret", setupIntent.getClientSecret());
            response.put("setupIntentId", setupIntent.getId());
            
            return response;
            
        } catch (StripeException e) {
            log.error("Error creating setup intent for customer: {}", customerEmail, e);
            return createErrorResponse("Failed to create setup intent: " + e.getMessage());
        }
    }

    /**
     * Process off-session payment for voice orders
     */
    public Map<String, Object> processVoicePayment(String customerEmail, Order order, String paymentMethodId) {
        try {
            Stripe.apiKey = stripeSecretKey;
            
            // Find customer
            Optional<Customer> customerOpt = customerRepository.findByEmail(customerEmail);
            if (customerOpt.isEmpty()) {
                return createErrorResponse("Customer not found");
            }
            
            Customer customer = customerOpt.get();
            String stripeCustomerId = customer.getStripeCustomerId();
            
            if (stripeCustomerId == null) {
                return createErrorResponse("Customer has no saved payment methods");
            }
            
            // Create off-session payment intent
            long amountInCents = order.getTotalAmount().multiply(new BigDecimal("100")).longValue();
            
            PaymentIntentCreateParams params = PaymentIntentCreateParams.builder()
                    .setAmount(amountInCents)
                    .setCurrency("usd")
                    .setCustomer(stripeCustomerId)
                    .setPaymentMethod(paymentMethodId)
                    .setOffSession(true)
                    .setConfirm(true)
                    .setDescription("Voice order #" + order.getId())
                    .putMetadata("orderId", order.getId().toString())
                    .putMetadata("voice_payment", "true")
                    .putMetadata("customer_email", customerEmail)
                    .build();
            
            PaymentIntent paymentIntent = PaymentIntent.create(params);
            
            // Create payment record
            Payment payment = new Payment();
            payment.setOrderId(order.getId());
            payment.setAmount(order.getTotalAmount());
            payment.setPaymentMethod("VOICE_STRIPE");
            payment.setTransactionId(paymentIntent.getId());
            payment.setStatus(PaymentStatus.INITIATED);
            paymentRepository.save(payment);
            
            // Handle payment status
            return handlePaymentIntentStatus(paymentIntent, order, payment);
            
        } catch (StripeException e) {
            log.error("Error processing voice payment for order: {}", order.getId(), e);
            return handleStripeError(e);
        }
    }

    /**
     * Handle authentication required scenarios
     */
    public Map<String, Object> handleAuthenticationRequired(String paymentIntentId) {
        try {
            Stripe.apiKey = stripeSecretKey;
            
            PaymentIntent originalIntent = PaymentIntent.retrieve(paymentIntentId);
            
            // Create new payment intent with authentication allowed
            PaymentIntentCreateParams params = PaymentIntentCreateParams.builder()
                    .setAmount(originalIntent.getAmount())
                    .setCurrency(originalIntent.getCurrency())
                    .setPaymentMethod(originalIntent.getPaymentMethod())
                    .setCustomer(originalIntent.getCustomer())
                    .setOffSession(false) // Allow authentication
                    .setDescription(originalIntent.getDescription())
                    .putAllMetadata(originalIntent.getMetadata())
                    .build();
            
            PaymentIntent newIntent = PaymentIntent.create(params);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("requiresAction", true);
            response.put("clientSecret", newIntent.getClientSecret());
            response.put("paymentIntentId", newIntent.getId());
            
            return response;
            
        } catch (StripeException e) {
            log.error("Error handling authentication required: {}", paymentIntentId, e);
            return createErrorResponse("Failed to handle authentication: " + e.getMessage());
        }
    }

    /**
     * Get payment method recommendations for voice interface
     */
    public Map<String, Object> getVoicePaymentRecommendations(String customerEmail, String userAgent) {
        Map<String, Object> recommendations = new HashMap<>();
        
        try {
            // Get customer's saved payment methods
            Map<String, Object> paymentMethods = getCustomerPaymentMethods(customerEmail);
            
            if (!(Boolean) paymentMethods.get("success")) {
                return paymentMethods;
            }
            
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> methods = (List<Map<String, Object>>) paymentMethods.get("paymentMethods");
            @SuppressWarnings("unchecked")
            Map<String, Object> capabilities = (Map<String, Object>) paymentMethods.get("capabilities");
            
            // Determine best payment method based on device and saved methods
            String recommendedMethod = determineRecommendedPaymentMethod(methods, capabilities, userAgent);
            
            recommendations.put("success", true);
            recommendations.put("recommendedMethod", recommendedMethod);
            recommendations.put("availableMethods", methods);
            recommendations.put("capabilities", capabilities);
            recommendations.put("voiceInstructions", generateVoiceInstructions(methods, recommendedMethod));
            
        } catch (Exception e) {
            log.error("Error getting voice payment recommendations: {}", e.getMessage(), e);
            return createErrorResponse("Failed to get payment recommendations");
        }
        
        return recommendations;
    }

    // Helper methods

    private Map<String, Object> formatPaymentMethodForVoice(PaymentMethod paymentMethod) {
        Map<String, Object> formatted = new HashMap<>();
        formatted.put("id", paymentMethod.getId());
        formatted.put("type", paymentMethod.getType());
        
        if (paymentMethod.getCard() != null) {
            PaymentMethod.Card card = paymentMethod.getCard();
            formatted.put("brand", card.getBrand());
            formatted.put("last4", card.getLast4());
            formatted.put("expMonth", card.getExpMonth());
            formatted.put("expYear", card.getExpYear());
            
            // Voice-friendly description
            String voiceDescription = String.format("%s ending in %s", 
                    capitalizeFirst(card.getBrand()), card.getLast4());
            formatted.put("voiceDescription", voiceDescription);
            
            // Check for digital wallet
            if (card.getWallet() != null) {
                formatted.put("walletType", card.getWallet().getType());
                formatted.put("isDigitalWallet", true);
            } else {
                formatted.put("isDigitalWallet", false);
            }
        }
        
        return formatted;
    }

    private Map<String, Object> detectWalletCapabilities(List<PaymentMethod> paymentMethods) {
        Map<String, Object> capabilities = new HashMap<>();
        
        boolean hasApplePay = paymentMethods.stream()
                .anyMatch(pm -> pm.getCard() != null && pm.getCard().getWallet() != null && 
                         "apple_pay".equals(pm.getCard().getWallet().getType()));
        
        boolean hasGooglePay = paymentMethods.stream()
                .anyMatch(pm -> pm.getCard() != null && pm.getCard().getWallet() != null && 
                         "google_pay".equals(pm.getCard().getWallet().getType()));
        
        capabilities.put("hasApplePay", hasApplePay);
        capabilities.put("hasGooglePay", hasGooglePay);
        capabilities.put("hasDigitalWallet", hasApplePay || hasGooglePay);
        
        return capabilities;
    }

    private String determineRecommendedPaymentMethod(List<Map<String, Object>> methods, 
                                                   Map<String, Object> capabilities, 
                                                   String userAgent) {
        // If no saved methods, recommend setup
        if (methods.isEmpty()) {
            return "setup_required";
        }
        
        // Check for digital wallets first
        boolean isIOS = userAgent != null && userAgent.toLowerCase().contains("iphone");
        boolean isAndroid = userAgent != null && userAgent.toLowerCase().contains("android");
        
        if (isIOS && (Boolean) capabilities.get("hasApplePay")) {
            return "apple_pay";
        }
        
        if (isAndroid && (Boolean) capabilities.get("hasGooglePay")) {
            return "google_pay";
        }
        
        // Default to first saved card
        return "saved_card";
    }

    private List<String> generateVoiceInstructions(List<Map<String, Object>> methods, String recommendedMethod) {
        List<String> instructions = new ArrayList<>();
        
        if (methods.isEmpty()) {
            instructions.add("You'll need to save a payment method first. Say 'add payment method' to get started.");
            return instructions;
        }
        
        switch (recommendedMethod) {
            case "apple_pay":
                instructions.add("I recommend using Apple Pay for quick checkout. Just say 'pay with Apple Pay'.");
                break;
            case "google_pay":
                instructions.add("I recommend using Google Pay for quick checkout. Just say 'pay with Google Pay'.");
                break;
            case "saved_card":
                if (!methods.isEmpty()) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> firstCard = methods.get(0);
                    instructions.add("I can use your " + firstCard.get("voiceDescription") + 
                                   ". Just say 'pay with saved card' or 'use my card'.");
                }
                break;
        }
        
        // Add alternative options
        if (methods.size() > 1) {
            instructions.add("You can also say 'show payment options' to see all your saved methods.");
        }
        
        return instructions;
    }

    private Map<String, Object> handlePaymentIntentStatus(PaymentIntent paymentIntent, Order order, Payment payment) {
        Map<String, Object> response = new HashMap<>();
        
        switch (paymentIntent.getStatus()) {
            case "succeeded":
                // Update payment and order status
                payment.setStatus(PaymentStatus.PAID);
                paymentRepository.save(payment);
                
                order.setPaymentStatus(PaymentStatus.PAID);
                orderRepository.save(order);
                
                // Trigger confirmation emails and notifications
                try {
                    paymentService.sendConfirmationEmails(paymentIntent.getId(), order.getId());
                } catch (Exception e) {
                    log.error("Error sending confirmation emails: {}", e.getMessage());
                }
                
                response.put("success", true);
                response.put("status", "succeeded");
                response.put("message", "Payment successful! Your order has been confirmed.");
                break;
                
            case "requires_action":
                response.put("success", false);
                response.put("requiresAction", true);
                response.put("clientSecret", paymentIntent.getClientSecret());
                response.put("message", "Payment requires authentication. Please complete the verification.");
                break;
                
            case "processing":
                response.put("success", true);
                response.put("status", "processing");
                response.put("message", "Payment is being processed. You'll receive confirmation shortly.");
                break;
                
            default:
                response.put("success", false);
                response.put("error", "payment_failed");
                response.put("message", "Payment failed. Please try a different payment method.");
                break;
        }
        
        return response;
    }

    private Map<String, Object> handleStripeError(StripeException e) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        
        switch (e.getCode()) {
            case "card_declined":
                response.put("error", "card_declined");
                response.put("message", "Your card was declined. Please try a different payment method.");
                break;
            case "insufficient_funds":
                response.put("error", "insufficient_funds");
                response.put("message", "Insufficient funds. Please try a different payment method.");
                break;
            case "authentication_required":
                response.put("error", "authentication_required");
                response.put("message", "Authentication is required for this payment.");
                response.put("requiresAction", true);
                break;
            default:
                response.put("error", "payment_failed");
                response.put("message", "Payment failed: " + e.getMessage());
                break;
        }
        
        return response;
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
        params.put("description", "Voice ordering customer");
        
        com.stripe.model.Customer stripeCustomer = com.stripe.model.Customer.create(params);
        
        // Save Stripe customer ID
        customer.setStripeCustomerId(stripeCustomer.getId());
        customerRepository.save(customer);
        
        return stripeCustomer.getId();
    }

    private Map<String, Object> createSuccessResponse(List<Map<String, Object>> paymentMethods, 
                                                     Map<String, Object> capabilities) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("paymentMethods", paymentMethods);
        response.put("capabilities", capabilities);
        return response;
    }

    private Map<String, Object> createErrorResponse(String message) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("error", message);
        return response;
    }

    private String capitalizeFirst(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }
}