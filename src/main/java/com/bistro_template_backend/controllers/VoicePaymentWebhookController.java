package com.bistro_template_backend.controllers;

import com.bistro_template_backend.models.Order;
import com.bistro_template_backend.models.Payment;
import com.bistro_template_backend.models.PaymentStatus;
import com.bistro_template_backend.repositories.OrderRepository;
import com.bistro_template_backend.repositories.PaymentRepository;
import com.bistro_template_backend.services.PaymentService;
import com.bistro_template_backend.services.WebSocketOrderService;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.model.PaymentIntent;
import com.stripe.model.SetupIntent;
import com.stripe.net.Webhook;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/webhooks")
@RequiredArgsConstructor
@Slf4j
public class VoicePaymentWebhookController {

    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;
    private final PaymentService paymentService;
    private final WebSocketOrderService webSocketOrderService;

    @Value("${stripe.webhook.secret}")
    private String webhookSecret;

    /**
     * Handle Stripe webhook events for voice payments
     */
    @PostMapping("/stripe/voice-payments")
    public ResponseEntity<String> handleStripeWebhook(
            @RequestBody String payload,
            @RequestHeader("Stripe-Signature") String sigHeader) {

        if (webhookSecret == null || webhookSecret.isEmpty()) {
            log.warn("Stripe webhook secret not configured");
            return ResponseEntity.ok("Webhook received but not processed (no secret configured)");
        }

        Event event;
        
        try {
            event = Webhook.constructEvent(payload, sigHeader, webhookSecret);
        } catch (SignatureVerificationException e) {
            log.error("Invalid webhook signature: {}", e.getMessage());
            return ResponseEntity.badRequest().body("Invalid signature");
        } catch (Exception e) {
            log.error("Error parsing webhook: {}", e.getMessage());
            return ResponseEntity.badRequest().body("Invalid payload");
        }

        log.info("Received Stripe webhook event: {} - {}", event.getType(), event.getId());

        try {
            switch (event.getType()) {
                case "payment_intent.succeeded":
                    handlePaymentIntentSucceeded(event);
                    break;
                    
                case "payment_intent.payment_failed":
                    handlePaymentIntentFailed(event);
                    break;
                    
                case "payment_intent.requires_action":
                    handlePaymentIntentRequiresAction(event);
                    break;
                    
                case "setup_intent.succeeded":
                    handleSetupIntentSucceeded(event);
                    break;
                    
                case "setup_intent.setup_failed":
                    handleSetupIntentFailed(event);
                    break;
                    
                case "payment_method.attached":
                    handlePaymentMethodAttached(event);
                    break;
                    
                default:
                    log.info("Unhandled webhook event type: {}", event.getType());
                    break;
            }
            
            return ResponseEntity.ok("Webhook processed successfully");
            
        } catch (Exception e) {
            log.error("Error processing webhook event {}: {}", event.getType(), e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error processing webhook");
        }
    }

    private void handlePaymentIntentSucceeded(Event event) {
        PaymentIntent paymentIntent = (PaymentIntent) event.getDataObjectDeserializer()
                .getObject().orElse(null);
                
        if (paymentIntent == null) {
            log.error("PaymentIntent is null in succeeded event");
            return;
        }

        log.info("Payment succeeded: {}", paymentIntent.getId());
        
        // Check if this is a voice payment
        Map<String, String> metadata = paymentIntent.getMetadata();
        if (metadata != null && "true".equals(metadata.get("voice_payment"))) {
            handleVoicePaymentSuccess(paymentIntent);
        }
    }

    private void handlePaymentIntentFailed(Event event) {
        PaymentIntent paymentIntent = (PaymentIntent) event.getDataObjectDeserializer()
                .getObject().orElse(null);
                
        if (paymentIntent == null) {
            log.error("PaymentIntent is null in failed event");
            return;
        }

        log.info("Payment failed: {}", paymentIntent.getId());
        
        // Update payment status
        Payment payment = paymentRepository.findByTransactionId(paymentIntent.getId());
        if (payment != null) {
            payment.setStatus(PaymentStatus.FAILED);
            paymentRepository.save(payment);
            
            // Notify voice system of failure
            Map<String, String> metadata = paymentIntent.getMetadata();
            if (metadata != null && "true".equals(metadata.get("voice_payment"))) {
                notifyVoiceSystemOfFailure(payment, paymentIntent);
            }
        }
    }

    private void handlePaymentIntentRequiresAction(Event event) {
        PaymentIntent paymentIntent = (PaymentIntent) event.getDataObjectDeserializer()
                .getObject().orElse(null);
                
        if (paymentIntent == null) {
            log.error("PaymentIntent is null in requires_action event");
            return;
        }

        log.info("Payment requires action: {}", paymentIntent.getId());
        
        Map<String, String> metadata = paymentIntent.getMetadata();
        if (metadata != null && "true".equals(metadata.get("voice_payment"))) {
            notifyVoiceSystemOfAuthRequired(paymentIntent);
        }
    }

    private void handleSetupIntentSucceeded(Event event) {
        SetupIntent setupIntent = (SetupIntent) event.getDataObjectDeserializer()
                .getObject().orElse(null);
                
        if (setupIntent == null) {
            log.error("SetupIntent is null in succeeded event");
            return;
        }

        log.info("Setup intent succeeded: {}", setupIntent.getId());
        
        Map<String, String> metadata = setupIntent.getMetadata();
        if (metadata != null && "true".equals(metadata.get("voice_system"))) {
            notifyVoiceSystemOfSetupSuccess(setupIntent);
        }
    }

    private void handleSetupIntentFailed(Event event) {
        SetupIntent setupIntent = (SetupIntent) event.getDataObjectDeserializer()
                .getObject().orElse(null);
                
        if (setupIntent == null) {
            log.error("SetupIntent is null in failed event");
            return;
        }

        log.info("Setup intent failed: {}", setupIntent.getId());
        
        Map<String, String> metadata = setupIntent.getMetadata();
        if (metadata != null && "true".equals(metadata.get("voice_system"))) {
            notifyVoiceSystemOfSetupFailure(setupIntent);
        }
    }

    private void handlePaymentMethodAttached(Event event) {
        com.stripe.model.PaymentMethod paymentMethod = 
                (com.stripe.model.PaymentMethod) event.getDataObjectDeserializer()
                .getObject().orElse(null);
                
        if (paymentMethod == null) {
            log.error("PaymentMethod is null in attached event");
            return;
        }

        log.info("Payment method attached: {} to customer: {}", 
                paymentMethod.getId(), paymentMethod.getCustomer());
    }

    private void handleVoicePaymentSuccess(PaymentIntent paymentIntent) {
        try {
            String orderId = paymentIntent.getMetadata().get("orderId");
            if (orderId != null) {
                // Update payment and order status
                paymentService.updatePaymentStatus(paymentIntent.getId(), Long.valueOf(orderId));
                
                // Send voice notification
                notifyVoiceSystemOfSuccess(paymentIntent);
                
                log.info("Voice payment processed successfully for order: {}", orderId);
            }
        } catch (Exception e) {
            log.error("Error handling voice payment success: {}", e.getMessage(), e);
        }
    }

    private void notifyVoiceSystemOfSuccess(PaymentIntent paymentIntent) {
        // Implementation would send WebSocket message to voice interface
        // indicating successful payment
        Map<String, String> metadata = paymentIntent.getMetadata();
        String customerEmail = metadata.get("customer_email");
        
        if (customerEmail != null) {
            // Send success notification via WebSocket
            log.info("Sending voice payment success notification to customer: {}", customerEmail);
            // webSocketOrderService.notifyVoicePaymentSuccess(customerEmail, paymentIntent);
        }
    }

    private void notifyVoiceSystemOfFailure(Payment payment, PaymentIntent paymentIntent) {
        // Implementation would send WebSocket message to voice interface
        // indicating payment failure with suggested next steps
        Map<String, String> metadata = paymentIntent.getMetadata();
        String customerEmail = metadata.get("customer_email");
        
        if (customerEmail != null) {
            log.info("Sending voice payment failure notification to customer: {}", customerEmail);
            // webSocketOrderService.notifyVoicePaymentFailure(customerEmail, paymentIntent);
        }
    }

    private void notifyVoiceSystemOfAuthRequired(PaymentIntent paymentIntent) {
        // Implementation would send WebSocket message to voice interface
        // indicating authentication is required
        Map<String, String> metadata = paymentIntent.getMetadata();
        String customerEmail = metadata.get("customer_email");
        
        if (customerEmail != null) {
            log.info("Sending voice payment auth required notification to customer: {}", customerEmail);
            // webSocketOrderService.notifyVoicePaymentAuthRequired(customerEmail, paymentIntent);
        }
    }

    private void notifyVoiceSystemOfSetupSuccess(SetupIntent setupIntent) {
        Map<String, String> metadata = setupIntent.getMetadata();
        String customerEmail = metadata.get("customer_email");
        
        if (customerEmail != null) {
            log.info("Sending voice setup success notification to customer: {}", customerEmail);
            // webSocketOrderService.notifyVoiceSetupSuccess(customerEmail, setupIntent);
        }
    }

    private void notifyVoiceSystemOfSetupFailure(SetupIntent setupIntent) {
        Map<String, String> metadata = setupIntent.getMetadata();
        String customerEmail = metadata.get("customer_email");
        
        if (customerEmail != null) {
            log.info("Sending voice setup failure notification to customer: {}", customerEmail);
            // webSocketOrderService.notifyVoiceSetupFailure(customerEmail, setupIntent);
        }
    }
}