package com.bistro_template_backend.controllers;

import com.bistro_template_backend.dto.PaymentRequest;
import com.bistro_template_backend.models.Order;
import com.bistro_template_backend.models.Payment;
import com.bistro_template_backend.repositories.OrderRepository;
import com.bistro_template_backend.repositories.PaymentRepository;
import com.bistro_template_backend.services.PaymentService;
import com.stripe.Stripe;
import com.stripe.model.PaymentIntent;
import jakarta.mail.MessagingException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api/orders")
public class PaymentController {

    @Autowired
    OrderRepository orderRepository;

    @Autowired
    PaymentRepository paymentRepository;

    @Autowired
    PaymentService paymentService;

    @Value("${stripe.secretKey}")
    private String stripeSecretKey;

    /**
     * Example: Endpoint to handle Stripe payment initiation.
     * e.g. POST /api/orders/{orderId}/pay/stripe
     */
    @PostMapping("/{orderId}/pay/stripe")
    public ResponseEntity<?> initiateStripePayment(@PathVariable Long orderId,
                                                   @RequestBody PaymentRequest request) {
        // Check if payment intent already exists for this order
        List<Payment> existingPayments = paymentRepository.findByOrderIdOrderByCreatedAtDesc(orderId);
        if (!existingPayments.isEmpty()) {
            // Get the existing payment
            Payment existingPayment = existingPayments.get(0);
            String transactionId = existingPayment.getTransactionId();

            try {
                // Configure Stripe
                Stripe.apiKey = stripeSecretKey;

                // Retrieve the existing PaymentIntent from Stripe
                PaymentIntent paymentIntent = PaymentIntent.retrieve(transactionId);

                // Return the client secret and payment intent ID
                Map<String, Object> result = new HashMap<>();
                result.put("clientSecret", paymentIntent.getClientSecret());
                result.put("paymentIntentId", paymentIntent.getId());

                return ResponseEntity.ok(result);
            } catch (Exception e) {
                // If there's an error retrieving (e.g., the payment intent was deleted on Stripe),
                // create a new one
                e.printStackTrace();
                // Fall through to create a new payment intent
            }
        }

        // Otherwise create a new payment intent as before
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found"));

        Map<String, Object> result = paymentService.createStripePaymentIntent(order, request);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/{orderId}/confirmPayment/stripe")
    public void confirmStripePayment(@PathVariable Long orderId) throws MessagingException {
        // 1. Find the most recent payment for this order
        List<Payment> payments = paymentRepository.findByOrderIdOrderByCreatedAtDesc(orderId);

        if (payments.isEmpty()) {
            throw new RuntimeException("No payment found for order: " + orderId);
        }

        // Get the most recent payment
        Payment payment = payments.get(0);

        // Update payment and order status immediately
        paymentService.updatePaymentStatus(payment.getTransactionId(), orderId);

        // Trigger email sending in a separate thread
        CompletableFuture.runAsync(() -> {
            try {
                paymentService.sendConfirmationEmails(payment.getTransactionId(), orderId);
            } catch (Exception e) {
                // Log the error but don't block the response
                System.err.println("Error sending confirmation emails: " + e.getMessage());
            }
        });
    }
    /**
     * Example: Endpoint to handle PayPal payment initiation.
     * e.g. POST /api/orders/{orderId}/pay/paypal
     */
//    @PostMapping("/{orderId}/pay/paypal")
//    public ResponseEntity<?> initiatePayPalPayment(@PathVariable Long orderId,
//                                                   @RequestBody PaymentRequest request) {
//        Order order = orderRepository.findById(orderId)
//                .orElseThrow(() -> new RuntimeException("Order not found"));
//
//        // If we need to recalc or validate amounts
//        // e.g. BigDecimal total = order.getTotalAmount();
//
//        // Use PaymentService to create a PayPal order or return an approval link
//        Map<String, Object> result = paymentService.createPayPalOrder(order, request);
//
//        return ResponseEntity.ok(result);
//    }
}