package com.bistro_template_backend.controllers;

import com.bistro_template_backend.dto.PaymentRequest;
import com.bistro_template_backend.models.Order;
import com.bistro_template_backend.models.Payment;
import com.bistro_template_backend.repositories.OrderRepository;
import com.bistro_template_backend.repositories.PaymentRepository;
import com.bistro_template_backend.services.PaymentService;
import jakarta.mail.MessagingException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
@RestController
@RequestMapping("/api/orders")
public class PaymentController {

    @Autowired
    OrderRepository orderRepository;

    @Autowired
    PaymentRepository paymentRepository;

    @Autowired
    PaymentService paymentService;


    /**
     * Example: Endpoint to handle Stripe payment initiation.
     * e.g. POST /api/orders/{orderId}/pay/stripe
     */
    @PostMapping("/{orderId}/pay/stripe")
    public ResponseEntity<?> initiateStripePayment(@PathVariable Long orderId,
                                                   @RequestBody PaymentRequest request) {
        // 1. Find Order
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found"));

        // 2. Validate amounts or finalize order total if needed
        //    For example, we might confirm order.totalAmount matches request.amount

        // 3. Use PaymentService to create PaymentIntent with Stripe
        Map<String, Object> result = paymentService.createStripePaymentIntent(order, request);

        // 4. Return clientSecret or relevant data to the frontend
        return ResponseEntity.ok(result);
    }

    @PostMapping("/{orderId}/confirmPayment/stripe")
    public void confirmStripePayment(@PathVariable Long orderId) throws MessagingException {
        // 1. Find Order
        Payment payment = paymentRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Payment not found"));

        paymentService.confirmPayment(payment.getTransactionId(), orderId);
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