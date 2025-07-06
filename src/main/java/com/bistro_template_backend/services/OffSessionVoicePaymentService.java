package com.bistro_template_backend.services;

import com.bistro_template_backend.models.Customer;
import com.bistro_template_backend.models.Order;
import com.bistro_template_backend.models.OrderStatus;
import com.bistro_template_backend.models.PaymentStatus;
import com.bistro_template_backend.repositories.CustomerRepository;
import com.bistro_template_backend.repositories.OrderRepository;
import com.bistro_template_backend.services.PaymentService;
import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.stripe.model.PaymentMethod;
import com.stripe.param.PaymentIntentCreateParams;
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
public class OffSessionVoicePaymentService {

    private final CustomerRepository customerRepository;
    private final OrderRepository orderRepository;
    private final PaymentService paymentService;

    @Value("${stripe.secretKey}")
    private String stripeSecretKey;

    /**
     * Process off-session payment using saved payment method
     */
    public Map<String, Object> processOffSessionPayment(String customerEmail, Long orderId, String paymentMethodType) {
        try {
            Stripe.apiKey = stripeSecretKey;
            
            // Find customer and order
            Customer customer = findCustomerByEmail(customerEmail);
            if (customer == null || customer.getStripeCustomerId() == null) {
                return createErrorResponse("Customer not found or not set up for payments");
            }

            Optional<Order> orderOpt = orderRepository.findById(orderId);
            if (orderOpt.isEmpty()) {
                return createErrorResponse("Order not found");
            }

            Order order = orderOpt.get();
            if (order.getTotalAmount() == null || order.getTotalAmount().compareTo(BigDecimal.ZERO) <= 0) {
                return createErrorResponse("Invalid order amount");
            }

            // Find the appropriate saved payment method
            log.info("Looking for saved payment method of type '{}' for Stripe customer: {}", paymentMethodType, customer.getStripeCustomerId());
            PaymentMethod paymentMethod = findSavedPaymentMethod(customer.getStripeCustomerId(), paymentMethodType);
            if (paymentMethod == null) {
                log.error("No matching saved payment method found for type '{}' for customer: {}", paymentMethodType, customerEmail);
                return createErrorResponse("No matching saved payment method found for " + paymentMethodType);
            }
            
            log.info("Found payment method: {} for off-session payment", paymentMethod.getId());

            // Calculate amount in cents
            long amountInCents = order.getTotalAmount().multiply(new BigDecimal(100)).longValue();

            // Create payment intent for off-session payment
            PaymentIntentCreateParams params = PaymentIntentCreateParams.builder()
                    .setAmount(amountInCents)
                    .setCurrency("usd")
                    .setCustomer(customer.getStripeCustomerId())
                    .setPaymentMethod(paymentMethod.getId())
                    .setConfirm(true)
                    .setOffSession(true)
                    .putMetadata("order_id", orderId.toString())
                    .putMetadata("orderId", orderId.toString()) // For webhook compatibility
                    .putMetadata("customer_email", customerEmail)
                    .putMetadata("payment_type", "voice_off_session")
                    .putMetadata("payment_method_type", paymentMethodType)
                    .putMetadata("voice_payment", "true") // For webhook detection
                    .setDescription("Voice order payment for order #" + orderId)
                    .build();

            log.info("Creating off-session payment intent for order {} with amount {} cents", orderId, amountInCents);
            PaymentIntent paymentIntent = PaymentIntent.create(params);
            
            log.info("Payment intent created with ID: {} and status: {}", paymentIntent.getId(), paymentIntent.getStatus());
            
            // Check payment status
            if ("succeeded".equals(paymentIntent.getStatus())) {
                // Payment succeeded immediately
                order.setStatus(OrderStatus.PENDING); // Order is now paid but still being prepared
                order.setPaymentStatus(PaymentStatus.PAID);
                order.setStripePaymentIntentId(paymentIntent.getId());
                
                // Calculate and set estimated pickup time
                LocalDateTime estimatedPickupTime = calculateEstimatedPickupTime(order);
                order.setEstimatedPickupTime(estimatedPickupTime);
                orderRepository.save(order);
                
                log.info("Off-session payment succeeded for order {} using {}", orderId, paymentMethodType);
                
                // Send confirmation emails asynchronously to avoid blocking payment response
                CompletableFuture.runAsync(() -> {
                    try {
                        paymentService.sendConfirmationEmails(paymentIntent.getId(), orderId);
                        log.info("Confirmation emails sent for voice order {}", orderId);
                    } catch (Exception e) {
                        log.error("Failed to send confirmation emails for voice order {}: {}", orderId, e.getMessage(), e);
                        // Don't fail the payment if email fails
                    }
                });
                
                String pickupTimeStr = formatPickupTimeForVoice(estimatedPickupTime);
                
                return Map.of(
                    "success", true,
                    "status", "succeeded",
                    "paymentIntentId", paymentIntent.getId(),
                    "orderId", orderId,
                    "amount", String.format("%.2f", order.getTotalAmount()),
                    "processingTime", "instant",
                    "emailSent", true,
                    "estimatedPickupTime", pickupTimeStr,
                    "voiceMessage", String.format("Perfect! Your %s payment of $%.2f has been processed instantly. Order #%d is confirmed and will be ready for pickup %s. You'll receive an email confirmation shortly!", 
                        getPaymentMethodDisplayName(paymentMethodType), order.getTotalAmount(), orderId, pickupTimeStr)
                );
                
            } else if ("requires_action".equals(paymentIntent.getStatus())) {
                // Payment requires additional authentication
                log.warn("Off-session payment requires action for order {}", orderId);
                
                return Map.of(
                    "success", true,
                    "requiresAction", true,
                    "status", "requires_action",
                    "paymentIntentId", paymentIntent.getId(),
                    "clientSecret", paymentIntent.getClientSecret(),
                    "voiceMessage", String.format("Your %s payment requires verification. Please complete the authentication on your device.", 
                        getPaymentMethodDisplayName(paymentMethodType))
                );
                
            } else {
                // Payment failed
                log.error("Off-session payment failed for order {} with status: {}", orderId, paymentIntent.getStatus());
                
                // Get more details about the failure
                String failureReason = paymentIntent.getLastPaymentError() != null ? 
                    paymentIntent.getLastPaymentError().getMessage() : "Unknown error";
                log.error("Payment failure reason: {}", failureReason);
                
                return createErrorResponse(String.format("Payment failed with status: %s. Please try a different payment method.", 
                    paymentIntent.getStatus()));
            }
            
        } catch (StripeException e) {
            log.error("Stripe error during off-session payment for order {}: {}", orderId, e.getMessage(), e);
            
            if ("authentication_required".equals(e.getCode())) {
                return Map.of(
                    "success", false,
                    "requiresAction", true,
                    "error", "authentication_required",
                    "voiceMessage", "Your payment method requires verification. Please update your payment method in your profile."
                );
            } else if ("card_declined".equals(e.getCode())) {
                return createErrorResponse("Your card was declined. Please try a different payment method or update your card information.");
            } else {
                return createErrorResponse("Payment processing failed: " + e.getUserMessage());
            }
            
        } catch (Exception e) {
            log.error("Unexpected error during off-session payment for order {}: {}", orderId, e.getMessage(), e);
            return createErrorResponse("Payment processing failed. Please try again.");
        }
    }

    /**
     * Get available saved payment methods for voice ordering
     */
    public Map<String, Object> getVoicePaymentOptions(String customerEmail) {
        try {
            Stripe.apiKey = stripeSecretKey;
            
            Customer customer = findCustomerByEmail(customerEmail);
            if (customer == null || customer.getStripeCustomerId() == null) {
                return createErrorResponse("Customer not found");
            }

            // Get saved payment methods
            var paymentMethods = PaymentMethod.list(
                Map.of("customer", customer.getStripeCustomerId(), "type", "card")
            );

            List<Map<String, Object>> voiceOptions = new ArrayList<>();
            
            for (PaymentMethod pm : paymentMethods.getData()) {
                if ("card".equals(pm.getType()) && pm.getCard() != null) {
                    var card = pm.getCard();
                    
                    Map<String, Object> option = new HashMap<>();
                    option.put("id", pm.getId());
                    option.put("type", determineVoicePaymentType(pm));
                    option.put("ready_for_voice", true);
                    
                    // Create voice-friendly description
                    if (card.getWallet() != null) {
                        String walletType = card.getWallet().getType();
                        if ("apple_pay".equals(walletType)) {
                            option.put("voice_command", "apple_pay");
                            option.put("voice_description", "Apple Pay");
                        } else if ("google_pay".equals(walletType)) {
                            option.put("voice_command", "google_pay");
                            option.put("voice_description", "Google Pay");
                        }
                    } else {
                        option.put("voice_command", "card");
                        option.put("voice_description", String.format("%s ending in %s", 
                            card.getBrand().substring(0, 1).toUpperCase() + card.getBrand().substring(1), 
                            card.getLast4()));
                    }
                    
                    voiceOptions.add(option);
                }
            }

            return Map.of(
                "success", true,
                "paymentOptions", voiceOptions,
                "hasApplePay", voiceOptions.stream().anyMatch(o -> "apple_pay".equals(o.get("voice_command"))),
                "hasGooglePay", voiceOptions.stream().anyMatch(o -> "google_pay".equals(o.get("voice_command"))),
                "hasCards", voiceOptions.stream().anyMatch(o -> "card".equals(o.get("voice_command"))),
                "voiceMessage", voiceOptions.isEmpty() ? 
                    "You don't have any saved payment methods for voice ordering." :
                    String.format("You have %d payment method%s available for voice ordering.", 
                        voiceOptions.size(), voiceOptions.size() > 1 ? "s" : "")
            );
            
        } catch (Exception e) {
            log.error("Error getting voice payment options for customer {}: {}", customerEmail, e.getMessage(), e);
            return createErrorResponse("Failed to retrieve payment options");
        }
    }

    /**
     * Validate that a payment method can be used for off-session payments
     */
    public Map<String, Object> validateOffSessionEligibility(String customerEmail, String paymentMethodType) {
        try {
            Stripe.apiKey = stripeSecretKey;
            
            Customer customer = findCustomerByEmail(customerEmail);
            if (customer == null || customer.getStripeCustomerId() == null) {
                return Map.of(
                    "success", false,
                    "eligible", false,
                    "reason", "Customer not found"
                );
            }

            PaymentMethod paymentMethod = findSavedPaymentMethod(customer.getStripeCustomerId(), paymentMethodType);
            if (paymentMethod == null) {
                return Map.of(
                    "success", true,
                    "eligible", false,
                    "reason", "No saved " + paymentMethodType + " method found",
                    "suggestion", "Please add " + getPaymentMethodDisplayName(paymentMethodType) + " to your profile first"
                );
            }

            // Check if payment method is set up for off-session usage
            boolean offSessionReady = "off_session".equals(paymentMethod.getMetadata().get("usage")) ||
                                    paymentMethod.getCard().getWallet() != null; // Digital wallets are usually off-session ready

            return Map.of(
                "success", true,
                "eligible", offSessionReady,
                "paymentMethodId", paymentMethod.getId(),
                "reason", offSessionReady ? "Ready for voice ordering" : "Requires authentication setup",
                "estimatedProcessingTime", "2-3 seconds",
                "voiceDescription", getVoiceDescription(paymentMethod)
            );
            
        } catch (Exception e) {
            log.error("Error validating off-session eligibility: {}", e.getMessage(), e);
            return Map.of(
                "success", false,
                "eligible", false,
                "reason", "Validation failed"
            );
        }
    }

    // Helper methods
    
    private Customer findCustomerByEmail(String customerEmail) {
        return customerRepository.findByEmail(customerEmail).orElse(null);
    }

    private PaymentMethod findSavedPaymentMethod(String stripeCustomerId, String paymentMethodType) throws StripeException {
        log.info("Searching for payment method type '{}' among customer's saved methods", paymentMethodType);
        var paymentMethods = PaymentMethod.list(
            Map.of("customer", stripeCustomerId, "type", "card")
        );

        log.info("Found {} total payment methods for customer", paymentMethods.getData().size());
        
        for (PaymentMethod pm : paymentMethods.getData()) {
            if ("card".equals(pm.getType()) && pm.getCard() != null) {
                var card = pm.getCard();
                String walletType = card.getWallet() != null ? card.getWallet().getType() : "none";
                log.info("Checking payment method {}: brand={}, wallet={}", pm.getId(), card.getBrand(), walletType);
                
                // Match based on payment method type
                switch (paymentMethodType) {
                    case "apple_pay":
                        if (card.getWallet() != null && "apple_pay".equals(card.getWallet().getType())) {
                            log.info("Found matching Apple Pay payment method: {}", pm.getId());
                            return pm;
                        }
                        break;
                    case "google_pay":
                        if (card.getWallet() != null && "google_pay".equals(card.getWallet().getType())) {
                            log.info("Found matching Google Pay payment method: {}", pm.getId());
                            return pm;
                        }
                        break;
                    case "card":
                        // Return any card-based payment method (including Link, traditional cards, etc.)
                        // Exclude only Apple Pay and Google Pay
                        if (card.getWallet() == null || 
                            (!"apple_pay".equals(card.getWallet().getType()) && 
                             !"google_pay".equals(card.getWallet().getType()))) {
                            log.info("Found matching card payment method: {} (wallet: {})", pm.getId(), walletType);
                            return pm;
                        }
                        break;
                }
            }
        }
        
        log.warn("No matching payment method found for type: {}", paymentMethodType);
        
        return null;
    }

    private String determineVoicePaymentType(PaymentMethod paymentMethod) {
        if (paymentMethod.getCard() != null && paymentMethod.getCard().getWallet() != null) {
            return paymentMethod.getCard().getWallet().getType();
        }
        return "card";
    }

    private String getPaymentMethodDisplayName(String paymentMethodType) {
        switch (paymentMethodType) {
            case "apple_pay": return "Apple Pay";
            case "google_pay": return "Google Pay";
            case "card": return "card";
            default: return paymentMethodType;
        }
    }

    private String getVoiceDescription(PaymentMethod paymentMethod) {
        if (paymentMethod.getCard() != null) {
            var card = paymentMethod.getCard();
            if (card.getWallet() != null) {
                return getPaymentMethodDisplayName(card.getWallet().getType());
            } else {
                return String.format("%s ending in %s", 
                    card.getBrand().substring(0, 1).toUpperCase() + card.getBrand().substring(1), 
                    card.getLast4());
            }
        }
        return "Unknown payment method";
    }

    private Map<String, Object> createErrorResponse(String message) {
        return Map.of(
            "success", false,
            "error", message,
            "voiceMessage", message
        );
    }
    
    /**
     * Calculate estimated pickup time based on order complexity
     */
    private LocalDateTime calculateEstimatedPickupTime(Order order) {
        // Base preparation time: 15 minutes
        int baseMinutes = 15;
        
        // Add 2 minutes per item (up to 10 extra minutes)
        int itemCount = getOrderItemCount(order);
        int itemMinutes = Math.min(itemCount * 2, 10);
        
        // Add 5 minutes during peak hours (11-2 PM, 5-8 PM)
        LocalDateTime now = LocalDateTime.now();
        int hour = now.getHour();
        int peakMinutes = 0;
        if ((hour >= 11 && hour <= 14) || (hour >= 17 && hour <= 20)) {
            peakMinutes = 5;
        }
        
        // Add 2 minutes for voice orders (slight processing overhead)
        int voiceMinutes = 2;
        
        int totalMinutes = baseMinutes + itemMinutes + peakMinutes + voiceMinutes;
        
        log.info("Calculated pickup time for order {}: base={}, items={}, peak={}, voice={}, total={} minutes", 
            order.getId(), baseMinutes, itemMinutes, peakMinutes, voiceMinutes, totalMinutes);
        
        return now.plusMinutes(totalMinutes);
    }
    
    /**
     * Get the number of items in an order
     */
    private int getOrderItemCount(Order order) {
        // This is a simplified calculation. In a real implementation, you'd query the OrderItem table
        // For now, we'll estimate based on order total (assuming $12 average per item)
        if (order.getTotalAmount() != null) {
            return Math.max(1, order.getTotalAmount().divide(new BigDecimal("12"), java.math.RoundingMode.HALF_UP).intValue());
        }
        return 1; // Default to 1 item if no total available
    }
    
    /**
     * Format pickup time for voice response
     */
    private String formatPickupTimeForVoice(LocalDateTime pickupTime) {
        LocalDateTime now = LocalDateTime.now();
        long minutesUntilPickup = java.time.Duration.between(now, pickupTime).toMinutes();
        
        if (minutesUntilPickup <= 15) {
            return "in about 15 minutes";
        } else if (minutesUntilPickup <= 20) {
            return "in about 20 minutes";
        } else if (minutesUntilPickup <= 25) {
            return "in about 25 minutes";
        } else if (minutesUntilPickup <= 30) {
            return "in about 30 minutes";
        } else {
            // Format as "at 2:30 PM" for longer wait times
            return "at " + pickupTime.format(java.time.format.DateTimeFormatter.ofPattern("h:mm a"));
        }
    }
}