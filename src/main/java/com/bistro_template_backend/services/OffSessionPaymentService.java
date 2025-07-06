package com.bistro_template_backend.services;

import com.bistro_template_backend.models.*;
import com.bistro_template_backend.repositories.*;
import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.stripe.model.PaymentMethod;
import com.stripe.param.PaymentIntentCreateParams;
import com.stripe.param.PaymentMethodListParams;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;

@Service
@RequiredArgsConstructor
@Slf4j
public class OffSessionPaymentService {

    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;
    private final CustomerRepository customerRepository;
    private final PaymentService paymentService;
    private final WebSocketOrderService webSocketOrderService;

    @Value("${stripe.secretKey}")
    private String stripeSecretKey;

    /**
     * Process off-session payment with automatic retry and fallback logic
     */
    public Map<String, Object> processOffSessionPayment(String customerEmail, Order order, 
                                                       String paymentMethodId, 
                                                       Map<String, String> paymentOptions) {
        try {
            Stripe.apiKey = stripeSecretKey;
            
            // Validate customer and payment method
            ValidationResult validation = validateOffSessionPayment(customerEmail, paymentMethodId);
            if (!validation.isValid()) {
                return createErrorResponse(validation.getErrorMessage());
            }
            
            Customer customer = validation.getCustomer();
            
            // Create off-session payment intent with optimized settings
            PaymentIntent paymentIntent = createOptimizedPaymentIntent(customer, order, paymentMethodId, paymentOptions);
            
            // Create payment record
            Payment payment = createPaymentRecord(order, paymentIntent, "VOICE_OFF_SESSION");
            
            // Process payment based on its status
            return handleOffSessionPaymentResult(paymentIntent, order, payment, customer);
            
        } catch (StripeException e) {
            log.error("Stripe error processing off-session payment for order: {}", order.getId(), e);
            return handleStripeError(e, order);
        } catch (Exception e) {
            log.error("Unexpected error processing off-session payment for order: {}", order.getId(), e);
            return createErrorResponse("Payment processing failed");
        }
    }

    /**
     * Process payment with specific payment method type (Apple Pay, Google Pay, etc.)
     */
    public Map<String, Object> processOffSessionPaymentWithType(String customerEmail, Order order, 
                                                               String paymentMethodType) {
        try {
            Stripe.apiKey = stripeSecretKey;
            
            // Find best payment method for the specified type
            Optional<PaymentMethod> paymentMethod = findBestPaymentMethodByType(customerEmail, paymentMethodType);
            
            if (paymentMethod.isEmpty()) {
                return createErrorResponse("No " + paymentMethodType + " payment method found for customer");
            }
            
            Map<String, String> paymentOptions = Map.of(
                "payment_method_type", paymentMethodType,
                "preferred_method", "true"
            );
            
            return processOffSessionPayment(customerEmail, order, paymentMethod.get().getId(), paymentOptions);
            
        } catch (Exception e) {
            log.error("Error processing {} payment for order: {}", paymentMethodType, order.getId(), e);
            return createErrorResponse("Failed to process " + paymentMethodType + " payment");
        }
    }

    /**
     * Retry failed off-session payment with different strategy
     */
    public Map<String, Object> retryOffSessionPayment(String originalPaymentIntentId, 
                                                     String alternativePaymentMethodId) {
        try {
            Stripe.apiKey = stripeSecretKey;
            
            // Retrieve original payment intent
            PaymentIntent originalIntent = PaymentIntent.retrieve(originalPaymentIntentId);
            String orderId = originalIntent.getMetadata().get("orderId");
            
            if (orderId == null) {
                return createErrorResponse("Original order not found");
            }
            
            Order order = orderRepository.findById(Long.valueOf(orderId))
                    .orElseThrow(() -> new RuntimeException("Order not found"));
            
            String customerEmail = originalIntent.getMetadata().get("customer_email");
            
            Map<String, String> retryOptions = Map.of(
                "retry_attempt", "true",
                "original_payment_intent", originalPaymentIntentId
            );
            
            return processOffSessionPayment(customerEmail, order, alternativePaymentMethodId, retryOptions);
            
        } catch (Exception e) {
            log.error("Error retrying off-session payment: {}", originalPaymentIntentId, e);
            return createErrorResponse("Payment retry failed");
        }
    }

    /**
     * Get payment processing recommendations based on customer history and device
     */
    public Map<String, Object> getOffSessionPaymentRecommendations(String customerEmail, String deviceInfo) {
        try {
            Stripe.apiKey = stripeSecretKey;
            
            Optional<Customer> customerOpt = customerRepository.findByEmail(customerEmail);
            if (customerOpt.isEmpty()) {
                return createErrorResponse("Customer not found");
            }
            
            Customer customer = customerOpt.get();
            if (customer.getStripeCustomerId() == null) {
                return createErrorResponse("Customer has no saved payment methods");
            }
            
            // Get customer's payment methods
            PaymentMethodListParams params = PaymentMethodListParams.builder()
                    .setCustomer(customer.getStripeCustomerId())
                    .setType(PaymentMethodListParams.Type.CARD)
                    .build();
            
            List<PaymentMethod> paymentMethods = PaymentMethod.list(params).getData();
            
            // Analyze and recommend best payment method
            Map<String, Object> recommendations = analyzePaymentMethods(paymentMethods, deviceInfo);
            recommendations.put("success", true);
            recommendations.put("customerEmail", customerEmail);
            
            return recommendations;
            
        } catch (Exception e) {
            log.error("Error getting off-session payment recommendations: {}", e.getMessage(), e);
            return createErrorResponse("Failed to get payment recommendations");
        }
    }

    /**
     * Validate off-session payment eligibility
     */
    public Map<String, Object> validateOffSessionEligibility(String customerEmail, String paymentMethodId) {
        try {
            Stripe.apiKey = stripeSecretKey;
            
            ValidationResult validation = validateOffSessionPayment(customerEmail, paymentMethodId);
            
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("eligible", validation.isValid());
            
            if (!validation.isValid()) {
                result.put("reason", validation.getErrorMessage());
                result.put("suggestions", validation.getSuggestions());
            } else {
                result.put("paymentMethod", formatPaymentMethodInfo(validation.getPaymentMethod()));
                result.put("estimatedProcessingTime", "1-3 seconds");
            }
            
            return result;
            
        } catch (Exception e) {
            log.error("Error validating off-session eligibility: {}", e.getMessage(), e);
            return createErrorResponse("Validation failed");
        }
    }

    // Helper methods

    private ValidationResult validateOffSessionPayment(String customerEmail, String paymentMethodId) 
            throws StripeException {
        
        // Validate customer
        Optional<Customer> customerOpt = customerRepository.findByEmail(customerEmail);
        if (customerOpt.isEmpty()) {
            return ValidationResult.invalid("Customer not found", List.of("Ensure customer email is correct"));
        }
        
        Customer customer = customerOpt.get();
        if (customer.getStripeCustomerId() == null) {
            return ValidationResult.invalid("Customer has no Stripe account", 
                    List.of("Customer needs to save a payment method first"));
        }
        
        // Validate payment method
        PaymentMethod paymentMethod = PaymentMethod.retrieve(paymentMethodId);
        if (!customer.getStripeCustomerId().equals(paymentMethod.getCustomer())) {
            return ValidationResult.invalid("Payment method doesn't belong to customer", 
                    List.of("Use a payment method saved by this customer"));
        }
        
        return ValidationResult.valid(customer, paymentMethod);
    }

    private PaymentIntent createOptimizedPaymentIntent(Customer customer, Order order, 
                                                      String paymentMethodId, 
                                                      Map<String, String> options) throws StripeException {
        
        long amountInCents = order.getTotalAmount().multiply(new BigDecimal("100")).longValue();
        
        PaymentIntentCreateParams.Builder paramsBuilder = PaymentIntentCreateParams.builder()
                .setAmount(amountInCents)
                .setCurrency("usd")
                .setCustomer(customer.getStripeCustomerId())
                .setPaymentMethod(paymentMethodId)
                .setOffSession(true)
                .setConfirm(true)
                .setDescription("Voice order #" + order.getId())
                .putMetadata("orderId", order.getId().toString())
                .putMetadata("voice_payment", "true")
                .putMetadata("customer_email", customer.getEmail())
                .putMetadata("payment_type", "off_session")
                .putMetadata("created_via", "voice_ordering");
        
        // Add options as metadata
        if (options != null) {
            options.forEach(paramsBuilder::putMetadata);
        }
        
        // Optimize for specific payment method types
        String paymentMethodType = options != null ? options.get("payment_method_type") : null;
        if ("apple_pay".equals(paymentMethodType) || "google_pay".equals(paymentMethodType)) {
            paramsBuilder.setReceiptEmail(customer.getEmail());
        }
        
        return PaymentIntent.create(paramsBuilder.build());
    }

    private Payment createPaymentRecord(Order order, PaymentIntent paymentIntent, String paymentMethod) {
        Payment payment = new Payment();
        payment.setOrderId(order.getId());
        payment.setAmount(order.getTotalAmount());
        payment.setPaymentMethod(paymentMethod);
        payment.setTransactionId(paymentIntent.getId());
        payment.setStatus(PaymentStatus.INITIATED);
        return paymentRepository.save(payment);
    }

    private Map<String, Object> handleOffSessionPaymentResult(PaymentIntent paymentIntent, 
                                                             Order order, Payment payment, 
                                                             Customer customer) {
        Map<String, Object> response = new HashMap<>();
        
        switch (paymentIntent.getStatus()) {
            case "succeeded":
                handleSuccessfulPayment(payment, order, customer, paymentIntent);
                response.put("success", true);
                response.put("status", "succeeded");
                response.put("message", "Payment processed successfully!");
                response.put("voiceMessage", "Great! Your payment went through and your order is confirmed.");
                response.put("orderId", order.getId());
                break;
                
            case "requires_action":
                response.put("success", false);
                response.put("requiresAction", true);
                response.put("paymentIntentId", paymentIntent.getId());
                response.put("clientSecret", paymentIntent.getClientSecret());
                response.put("message", "Payment requires authentication");
                response.put("voiceMessage", "I need to verify your payment. Please check your device for authentication.");
                break;
                
            case "processing":
                response.put("success", true);
                response.put("status", "processing");
                response.put("message", "Payment is being processed");
                response.put("voiceMessage", "Your payment is being processed. I'll confirm once it's complete.");
                break;
                
            default:
                payment.setStatus(PaymentStatus.FAILED);
                paymentRepository.save(payment);
                response.put("success", false);
                response.put("error", "payment_failed");
                response.put("message", "Payment failed");
                response.put("voiceMessage", "Sorry, your payment couldn't be processed. Let's try a different method.");
                break;
        }
        
        return response;
    }

    private void handleSuccessfulPayment(Payment payment, Order order, Customer customer, PaymentIntent paymentIntent) {
        // Update payment status
        payment.setStatus(PaymentStatus.PAID);
        paymentRepository.save(payment);
        
        // Update order status
        order.setPaymentStatus(PaymentStatus.PAID);
        orderRepository.save(order);
        
        // Update customer stats
        customer.setLastOrderDate(LocalDateTime.now());
        customer.setTotalOrders(customer.getTotalOrders() + 1);
        customerRepository.save(customer);
        
        // Send notifications asynchronously
        CompletableFuture.runAsync(() -> {
            try {
                // Send confirmation emails
                paymentService.sendConfirmationEmails(paymentIntent.getId(), order.getId());
                
                // Send WebSocket notification
                webSocketOrderService.notifyNewOrder(order);
                
                log.info("Successfully processed off-session payment for order: {} customer: {}", 
                        order.getId(), customer.getEmail());
                        
            } catch (Exception e) {
                log.error("Error sending notifications for successful payment: {}", e.getMessage(), e);
            }
        });
    }

    private Optional<PaymentMethod> findBestPaymentMethodByType(String customerEmail, String paymentMethodType) 
            throws StripeException {
        
        Optional<Customer> customerOpt = customerRepository.findByEmail(customerEmail);
        if (customerOpt.isEmpty()) {
            return Optional.empty();
        }
        
        Customer customer = customerOpt.get();
        if (customer.getStripeCustomerId() == null) {
            return Optional.empty();
        }
        
        PaymentMethodListParams params = PaymentMethodListParams.builder()
                .setCustomer(customer.getStripeCustomerId())
                .setType(PaymentMethodListParams.Type.CARD)
                .build();
        
        List<PaymentMethod> paymentMethods = PaymentMethod.list(params).getData();
        
        // Find payment method matching the requested type
        return paymentMethods.stream()
                .filter(pm -> matchesPaymentMethodType(pm, paymentMethodType))
                .findFirst();
    }

    private boolean matchesPaymentMethodType(PaymentMethod paymentMethod, String requestedType) {
        if (paymentMethod.getCard() == null) return false;
        
        PaymentMethod.Card card = paymentMethod.getCard();
        
        switch (requestedType.toLowerCase()) {
            case "apple_pay":
                return card.getWallet() != null && "apple_pay".equals(card.getWallet().getType());
            case "google_pay":
                return card.getWallet() != null && "google_pay".equals(card.getWallet().getType());
            case "card":
                return card.getWallet() == null; // Regular card, not wallet
            default:
                return false;
        }
    }

    private Map<String, Object> analyzePaymentMethods(List<PaymentMethod> paymentMethods, String deviceInfo) {
        Map<String, Object> analysis = new HashMap<>();
        
        // Categorize payment methods
        List<PaymentMethod> applePayMethods = paymentMethods.stream()
                .filter(pm -> pm.getCard() != null && pm.getCard().getWallet() != null && 
                             "apple_pay".equals(pm.getCard().getWallet().getType()))
                .toList();
        
        List<PaymentMethod> googlePayMethods = paymentMethods.stream()
                .filter(pm -> pm.getCard() != null && pm.getCard().getWallet() != null && 
                             "google_pay".equals(pm.getCard().getWallet().getType()))
                .toList();
        
        List<PaymentMethod> cardMethods = paymentMethods.stream()
                .filter(pm -> pm.getCard() != null && pm.getCard().getWallet() == null)
                .toList();
        
        // Device-based recommendations
        boolean isIOS = deviceInfo != null && deviceInfo.toLowerCase().contains("iphone");
        boolean isAndroid = deviceInfo != null && deviceInfo.toLowerCase().contains("android");
        
        String recommendedMethod = null;
        String recommendedId = null;
        
        if (isIOS && !applePayMethods.isEmpty()) {
            recommendedMethod = "apple_pay";
            recommendedId = applePayMethods.get(0).getId();
        } else if (isAndroid && !googlePayMethods.isEmpty()) {
            recommendedMethod = "google_pay";
            recommendedId = googlePayMethods.get(0).getId();
        } else if (!cardMethods.isEmpty()) {
            recommendedMethod = "card";
            recommendedId = cardMethods.get(0).getId();
        } else if (!paymentMethods.isEmpty()) {
            recommendedMethod = "default";
            recommendedId = paymentMethods.get(0).getId();
        }
        
        analysis.put("recommendedMethod", recommendedMethod);
        analysis.put("recommendedPaymentMethodId", recommendedId);
        analysis.put("hasApplePay", !applePayMethods.isEmpty());
        analysis.put("hasGooglePay", !googlePayMethods.isEmpty());
        analysis.put("hasCards", !cardMethods.isEmpty());
        analysis.put("totalMethods", paymentMethods.size());
        
        return analysis;
    }

    private Map<String, Object> formatPaymentMethodInfo(PaymentMethod paymentMethod) {
        Map<String, Object> info = new HashMap<>();
        info.put("id", paymentMethod.getId());
        info.put("type", paymentMethod.getType());
        
        if (paymentMethod.getCard() != null) {
            PaymentMethod.Card card = paymentMethod.getCard();
            info.put("brand", card.getBrand());
            info.put("last4", card.getLast4());
            info.put("voiceDescription", String.format("%s ending in %s", 
                    capitalizeFirst(card.getBrand()), card.getLast4()));
            
            if (card.getWallet() != null) {
                info.put("walletType", card.getWallet().getType());
            }
        }
        
        return info;
    }

    private Map<String, Object> handleStripeError(StripeException e, Order order) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("orderId", order.getId());
        
        switch (e.getCode()) {
            case "card_declined":
                response.put("error", "card_declined");
                response.put("message", "Card was declined");
                response.put("voiceMessage", "Your card was declined. Let's try a different payment method.");
                response.put("suggestedAction", "try_different_method");
                break;
                
            case "insufficient_funds":
                response.put("error", "insufficient_funds");
                response.put("message", "Insufficient funds");
                response.put("voiceMessage", "There aren't enough funds on this payment method. Would you like to try another one?");
                response.put("suggestedAction", "try_different_method");
                break;
                
            case "authentication_required":
                response.put("error", "authentication_required");
                response.put("message", "Authentication required");
                response.put("voiceMessage", "This payment needs verification. I'll help you complete the authentication.");
                response.put("requiresAction", true);
                response.put("suggestedAction", "authenticate");
                break;
                
            default:
                response.put("error", "payment_failed");
                response.put("message", "Payment failed: " + e.getMessage());
                response.put("voiceMessage", "Sorry, there was an issue processing your payment. Let's try again.");
                response.put("suggestedAction", "retry");
                break;
        }
        
        return response;
    }

    private Map<String, Object> createErrorResponse(String message) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("error", message);
        return response;
    }

    private String capitalizeFirst(String str) {
        if (str == null || str.isEmpty()) return str;
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }

    // Helper class for validation results
    private static class ValidationResult {
        private final boolean valid;
        private final String errorMessage;
        private final List<String> suggestions;
        private final Customer customer;
        private final PaymentMethod paymentMethod;

        private ValidationResult(boolean valid, String errorMessage, List<String> suggestions, 
                               Customer customer, PaymentMethod paymentMethod) {
            this.valid = valid;
            this.errorMessage = errorMessage;
            this.suggestions = suggestions;
            this.customer = customer;
            this.paymentMethod = paymentMethod;
        }

        public static ValidationResult valid(Customer customer, PaymentMethod paymentMethod) {
            return new ValidationResult(true, null, null, customer, paymentMethod);
        }

        public static ValidationResult invalid(String errorMessage, List<String> suggestions) {
            return new ValidationResult(false, errorMessage, suggestions, null, null);
        }

        public boolean isValid() { return valid; }
        public String getErrorMessage() { return errorMessage; }
        public List<String> getSuggestions() { return suggestions; }
        public Customer getCustomer() { return customer; }
        public PaymentMethod getPaymentMethod() { return paymentMethod; }
    }
}