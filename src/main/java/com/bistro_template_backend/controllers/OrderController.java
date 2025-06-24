package com.bistro_template_backend.controllers;

import com.bistro_template_backend.dto.CreateOrderRequest;
import com.bistro_template_backend.dto.PaymentRequest;
import com.bistro_template_backend.models.*;
import com.bistro_template_backend.repositories.CustomizationRepository;
import com.bistro_template_backend.repositories.OrderItemCustomizationRepository;
import com.bistro_template_backend.repositories.OrderItemRepository;
import com.bistro_template_backend.repositories.OrderRepository;
import com.bistro_template_backend.repositories.PaymentRepository;
import com.bistro_template_backend.services.MobilePaymentService;
import com.bistro_template_backend.services.PaymentService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import com.stripe.Stripe;
import com.stripe.model.PaymentIntent;
import com.stripe.param.PaymentIntentCreateParams;
import org.springframework.beans.factory.annotation.Value;

@RestController
@RequestMapping("/api/orders")
public class OrderController {
    @Value("${stripe.secretKey}")
    private String stripeSecretKey;

    private static final BigDecimal TAX_RATE = new BigDecimal("0.0825");

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final CustomizationRepository customizationRepository;
    private final OrderItemCustomizationRepository orderItemCustomizationRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentService paymentService;
    private final MobilePaymentService mobilePaymentService;

    // Constructor injection to avoid circular dependencies
    public OrderController(OrderRepository orderRepository,
                           OrderItemRepository orderItemRepository,
                           CustomizationRepository customizationRepository,
                           OrderItemCustomizationRepository orderItemCustomizationRepository,
                           PaymentRepository paymentRepository,
                           PaymentService paymentService,
                           MobilePaymentService mobilePaymentService) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.customizationRepository = customizationRepository;
        this.orderItemCustomizationRepository = orderItemCustomizationRepository;
        this.paymentRepository = paymentRepository;
        this.paymentService = paymentService;
        this.mobilePaymentService = mobilePaymentService;
    }

    /**
     * Debug endpoint to check order and payment status
     */
    @GetMapping("/{orderId}/debug")
    public ResponseEntity<?> debugOrder(@PathVariable Long orderId) {
        try {
            Order order = orderRepository.findById(orderId)
                    .orElseThrow(() -> new RuntimeException("Order not found"));

            List<Payment> payments = paymentRepository.findByOrderIdOrderByCreatedAtDesc(orderId);

            Map<String, Object> debug = new HashMap<>();
            debug.put("orderId", orderId);
            debug.put("orderExists", true);
            debug.put("orderStatus", order.getStatus());
            debug.put("paymentStatus", order.getPaymentStatus());
            debug.put("totalAmount", order.getTotalAmount());
            debug.put("subTotal", order.getSubTotal());
            debug.put("tax", order.getTax());
            debug.put("serviceFee", order.getServiceFee());
            debug.put("customerEmail", order.getCustomerEmail());
            debug.put("orderDate", order.getOrderDate());
            debug.put("paymentsCount", payments.size());

            if (!payments.isEmpty()) {
                Payment latestPayment = payments.get(0);
                debug.put("latestPaymentMethod", latestPayment.getPaymentMethod());
                debug.put("latestPaymentStatus", latestPayment.getStatus());
                debug.put("latestTransactionId", latestPayment.getTransactionId());
                debug.put("latestPaymentAmount", latestPayment.getAmount());
            }

            // Validate minimum amount
            boolean meetsMinimum = order.getTotalAmount().compareTo(new BigDecimal("0.50")) >= 0;
            debug.put("meetsStripeMinimum", meetsMinimum);

            return ResponseEntity.ok(debug);

        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("orderId", orderId);
            error.put("orderExists", false);
            error.put("error", e.getMessage());
            return ResponseEntity.ok(error);
        }
    }

    // ========== ORDER MANAGEMENT ENDPOINTS ==========

    @PostMapping
    public Order createOrder(@Valid @RequestBody CreateOrderRequest request) {
        // 1. Create a new Order
        Order newOrder = new Order();
        newOrder.setOrderDate(LocalDateTime.now());
        newOrder.setStatus(OrderStatus.PENDING);
        newOrder.setPaymentStatus(PaymentStatus.NOT_PAID);
        newOrder.setCustomerId(request.getCustomerId());
        newOrder.setCustomerName(request.getCustomerName());
        newOrder.setCustomerEmail(request.getCustomerEmail());
        newOrder.setCustomerPhone(request.getCustomerPhone());
        newOrder.setSpecialNotes(request.getSpecialNotes());

        // Save the order first to get an ID
        newOrder = orderRepository.save(newOrder);

        // 2. Calculate the subtotal
        BigDecimal subtotal = BigDecimal.ZERO;

        if (request.getItems() != null) {
            for (var cartItem : request.getItems()) {
                OrderItem orderItem = new OrderItem();
                orderItem.setOrderId(newOrder.getId());
                orderItem.setMenuItemId(cartItem.getMenuItemId());
                orderItem.setQuantity(cartItem.getQuantity());
                orderItem.setRewardItem(cartItem.isRewardItem()); // Add this field to OrderItem entity

                // Use price from the cart or look up in your MenuItem table
                BigDecimal itemPrice = cartItem.getPriceAtOrderTime();
                orderItem.setItemPrice(itemPrice);

                // Only add to subtotal if it's not a reward item
                if (!cartItem.isRewardItem()) {
                    BigDecimal itemTotal = itemPrice.multiply(BigDecimal.valueOf(cartItem.getQuantity()));
                    subtotal = subtotal.add(itemTotal);
                }

                // Save each OrderItem
                orderItemRepository.save(orderItem);

                // Handle Customizations for this OrderItem
                if (cartItem.getCustomizations() != null) {
                    for (var customization : cartItem.getCustomizations()) {
                        OrderItemCustomization orderItemCustomization = new OrderItemCustomization();
                        orderItemCustomization.setOrderItem(orderItem);

                        Customization customizationEntity = customizationRepository.findById(customization.getId())
                                .orElseThrow(() -> new RuntimeException("Customization not found"));

                        orderItemCustomization.setCustomization(customizationEntity);

                        orderItemCustomizationRepository.save(orderItemCustomization);
                    }
                }
            }
        }

        // Calculate tax
        BigDecimal taxAmount = subtotal.multiply(TAX_RATE);

        // 3. Calculate service fee and total amount
        BigDecimal serviceFee = subtotal.multiply(BigDecimal.valueOf(0.029)).add(BigDecimal.valueOf(0.30));
        BigDecimal totalAmount = subtotal.add(taxAmount).add(serviceFee);

        // Set calculated values
        newOrder.setSubTotal(subtotal);
        newOrder.setTax(taxAmount);
        newOrder.setServiceFee(serviceFee);
        newOrder.setTotalAmount(totalAmount);

        // Save the final order with calculated amounts
        newOrder = orderRepository.save(newOrder);

        return newOrder;
    }

    @GetMapping("/{orderId}")
    public Map<String, Object> getOrderDetails(@PathVariable Long orderId) {
        // 1. Fetch the Order
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found"));

        // 2. Fetch all OrderItems for this Order
        List<OrderItem> orderItems = orderItemRepository.findByOrderId(orderId);

        // 3. Construct a custom response
        Map<String, Object> response = new HashMap<>();
        response.put("order", order);
        response.put("orderItems", orderItems);

        return response;
    }

    // ========== PAYMENT CAPABILITY ENDPOINTS ==========

    /**
     * Get available payment methods based on device/browser capabilities
     */
    @GetMapping("/{orderId}/payment-methods")
    public ResponseEntity<?> getAvailablePaymentMethods(@PathVariable Long orderId,
                                                        HttpServletRequest request) {
        try {
            String userAgent = request.getHeader("User-Agent");

            Map<String, Object> capabilities = mobilePaymentService
                    .validateMobilePaymentCapabilities(userAgent, null);

            Map<String, Object> response = new HashMap<>();
            response.put("orderId", orderId);
            response.put("capabilities", capabilities);
            response.put("availableMethods", getMethodsList(capabilities));

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error getting payment methods: " + e.getMessage());
        }
    }

    // ========== PAYMENT INITIATION ENDPOINTS ==========

    /**
     * Enhanced Stripe payment initialization with ExpressCheckout support
     */
    @PostMapping("/{orderId}/pay/stripe")
    public ResponseEntity<?> initiateStripePayment(@PathVariable Long orderId,
                                                   @Valid @RequestBody PaymentRequest request) {
        try {
            Order order = orderRepository.findById(orderId)
                    .orElseThrow(() -> new RuntimeException("Order not found"));

            // Validate minimum amount
            if (order.getTotalAmount().compareTo(new BigDecimal("0.50")) < 0) {
                return ResponseEntity.badRequest().body(
                        "Order amount (" + order.getTotalAmount() + ") is below minimum charge amount of $0.50"
                );
            }

            // Check if payment intent already exists for this order
            List<Payment> existingPayments = paymentRepository.findByOrderIdOrderByCreatedAtDesc(orderId);
            if (!existingPayments.isEmpty()) {
                Payment existingPayment = existingPayments.get(0);

                if (existingPayment.getStatus() == PaymentStatus.INITIATED) {
                    try {
                        Stripe.apiKey = stripeSecretKey;
                        PaymentIntent paymentIntent = PaymentIntent.retrieve(existingPayment.getTransactionId());

                        Map<String, Object> result = new HashMap<>();
                        result.put("clientSecret", paymentIntent.getClientSecret());
                        result.put("paymentIntentId", existingPayment.getTransactionId());
                        result.put("message", "Using existing payment intent");
                        return ResponseEntity.ok(result);
                    } catch (Exception e) {
                        System.err.println("❌ Error retrieving existing PaymentIntent: [REDACTED]");
                        // Fall through to create new payment intent
                    }
                }
            }

            // Create new payment intent with enhanced configuration for ExpressCheckout
            Map<String, Object> result = createEnhancedPaymentIntent(order, request);
            return ResponseEntity.ok(result);

        } catch (Exception e) {
            System.err.println("❌ Error initiating Stripe payment: " + e.getMessage());
            return ResponseEntity.badRequest().body("Error initiating payment: " + e.getMessage());
        }
    }


    /**
     * Apple Pay specific endpoint (legacy support)
     */
    @PostMapping("/{orderId}/pay/applepay")
    public ResponseEntity<?> initiateApplePayPayment(@PathVariable Long orderId,
                                                     @RequestBody PaymentRequest request) {
        try {
            Order order = orderRepository.findById(orderId)
                    .orElseThrow(() -> new RuntimeException("Order not found"));

            request.setPaymentMethod("APPLE_PAY");
            Map<String, Object> result = mobilePaymentService.createApplePayIntent(order, request);

            // Add Apple Pay specific configuration
            Map<String, Object> applePayConfig = mobilePaymentService
                    .getMobilePaymentConfig(orderId.toString(), "apple_pay");
            result.put("config", applePayConfig);

            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error initiating Apple Pay: " + e.getMessage());
        }
    }

    /**
     * Google Pay specific endpoint (legacy support)
     */
    @PostMapping("/{orderId}/pay/googlepay")
    public ResponseEntity<?> initiateGooglePayPayment(@PathVariable Long orderId,
                                                      @RequestBody PaymentRequest request) {
        try {
            Order order = orderRepository.findById(orderId)
                    .orElseThrow(() -> new RuntimeException("Order not found"));

            // Validate order amount meets Stripe minimum (50 cents for USD)
            if (order.getTotalAmount().compareTo(new BigDecimal("0.50")) < 0) {
                return ResponseEntity.badRequest().body(
                        "Order amount (" + order.getTotalAmount() + ") is below minimum charge amount of $0.50"
                );
            }

            // Check if payment intent already exists for this order
            List<Payment> existingPayments = paymentRepository.findByOrderIdOrderByCreatedAtDesc(orderId);
            if (!existingPayments.isEmpty()) {
                Payment existingPayment = existingPayments.get(0);

                if (existingPayment.getStatus() == PaymentStatus.INITIATED) {
                    try {
                        Stripe.apiKey = stripeSecretKey;
                        PaymentIntent paymentIntent = PaymentIntent.retrieve(existingPayment.getTransactionId());

                        Map<String, Object> result = new HashMap<>();
                        result.put("clientSecret", paymentIntent.getClientSecret());
                        result.put("paymentIntentId", existingPayment.getTransactionId());
                        result.put("message", "Using existing payment intent");

                        // Add Google Pay specific configuration
                        Map<String, Object> googlePayConfig = mobilePaymentService
                                .getMobilePaymentConfig(orderId.toString(), "google_pay");
                        result.put("config", googlePayConfig);

                        return ResponseEntity.ok(result);
                    } catch (Exception e) {
                        System.err.println("❌ Error retrieving existing PaymentIntent: [REDACTED]");
                        // Fall through to create new payment intent
                    }
                }
            }

            // Create new payment intent
            request.setPaymentMethod("GOOGLE_PAY");
            request.setAmount(order.getTotalAmount());

            Map<String, Object> result = mobilePaymentService.createGooglePayIntent(order, request);

            // Add Google Pay specific configuration
            Map<String, Object> googlePayConfig = mobilePaymentService
                    .getMobilePaymentConfig(orderId.toString(), "google_pay");
            result.put("config", googlePayConfig);

            System.out.println("✅ Created new Google Pay PaymentIntent for order: " + orderId);

            return ResponseEntity.ok(result);
        } catch (Exception e) {
            System.err.println("❌ Error initiating Google Pay for order " + orderId + ": [REDACTED]");
            return ResponseEntity.badRequest().body("Error initiating Google Pay: " + e.getMessage());
        }
    }

    // ========== PAYMENT CONFIRMATION ENDPOINTS ==========

    // OrderController.java - Optimized createEnhancedPaymentIntent method
    private Map<String, Object> createEnhancedPaymentIntent(Order order, PaymentRequest request) {
        try {
            Stripe.apiKey = stripeSecretKey;

            long amountInCents = order.getTotalAmount()
                    .multiply(new BigDecimal("100")).longValue();

            String paymentMethod = request.getPaymentMethod();
            System.out.println("🔧 Creating PaymentIntent for method: " + paymentMethod);

            PaymentIntentCreateParams.Builder paramsBuilder = PaymentIntentCreateParams.builder()
                    .setAmount(amountInCents)
                    .setCurrency(request.getCurrency().toLowerCase())
                    .setDescription(request.getDescription())
                    .putMetadata("orderId", order.getId().toString())
                    .putMetadata("paymentMethod", paymentMethod)
                    .putMetadata("customerEmail", order.getCustomerEmail())
                    .putMetadata("customerName", order.getCustomerName())
                    // OPTIMIZED: Enhanced automatic payment methods for ExpressCheckout
                    .setAutomaticPaymentMethods(
                            PaymentIntentCreateParams.AutomaticPaymentMethods.builder()
                                    .setEnabled(true)
                                    // Allow redirects for PayPal, Klarna, etc.
                                    .setAllowRedirects(PaymentIntentCreateParams.AutomaticPaymentMethods.AllowRedirects.ALWAYS)
                                    .build()
                    )
                    // OPTIMIZED: Better capture settings for ExpressCheckout
                    .setCaptureMethod(PaymentIntentCreateParams.CaptureMethod.AUTOMATIC);

            // OPTIMIZED: Enhanced configuration for ExpressCheckout
            if ("express_checkout".equals(paymentMethod)) {
                System.out.println("🚀 Configuring ExpressCheckout PaymentIntent");

                // Add customer information if available
                if (order.getCustomerEmail() != null) {
                    paramsBuilder.setReceiptEmail(order.getCustomerEmail());
                }

                // OPTIMIZED: Set up for future usage to enable Link
//                paramsBuilder.setSetupFutureUsage(PaymentIntentCreateParams.SetupFutureUsage.OFF_SESSION);

                // OPTIMIZED: Add shipping information if available
                if (order.getCustomerName() != null) {
                    paramsBuilder.setShipping(
                            PaymentIntentCreateParams.Shipping.builder()
                                    .setName(order.getCustomerName())
                                    .setPhone(order.getCustomerPhone())
                                    // Add address if you collect it
                                    .setAddress(
                                            PaymentIntentCreateParams.Shipping.Address.builder()
                                                    .setLine1("123 Main St") // Use actual address if available
                                                    .setCity("Houston")
                                                    .setState("TX")
                                                    .setCountry("US")
                                                    .setPostalCode("77001")
                                                    .build()
                                    )
                                    .build()
                    );
                }
            }

            PaymentIntent paymentIntent = PaymentIntent.create(paramsBuilder.build());

            // Create payment record
            Payment payment = new Payment();
            payment.setOrderId(order.getId());
            payment.setAmount(order.getTotalAmount());
            payment.setPaymentMethod(paymentMethod.toUpperCase());
            payment.setTransactionId(paymentIntent.getId());
            payment.setStatus(PaymentStatus.INITIATED);
            paymentRepository.save(payment);

            Map<String, Object> response = new HashMap<>();
            response.put("clientSecret", paymentIntent.getClientSecret());
            response.put("paymentIntentId", paymentIntent.getId());
            response.put("paymentMethod", paymentMethod);

            // OPTIMIZED: Add additional metadata for ExpressCheckout
            if ("express_checkout".equals(paymentMethod)) {
                response.put("orderDetails", Map.of(
                        "orderId", order.getId(),
                        "customerName", order.getCustomerName(),
                        "customerEmail", order.getCustomerEmail(),
                        "amount", order.getTotalAmount(),
                        "currency", "USD"
                ));
            }

            System.out.println("✅ Created PaymentIntent for order: " + order.getId());

            return response;
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Error creating enhanced PaymentIntent: " + e.getMessage(), e);
        }
    }

    // OPTIMIZED: Enhanced payment confirmation endpoint
    @PostMapping("/{orderId}/confirmPayment/stripe")
    public ResponseEntity<?> confirmStripePayment(@PathVariable Long orderId,
                                                  @RequestBody(required = false) Map<String, String> customerData) {
        try {
            System.out.println("🔔 Confirming payment for order: " + orderId);

            // Find the most recent payment for this order
            List<Payment> payments = paymentRepository.findByOrderIdOrderByCreatedAtDesc(orderId);

            if (payments.isEmpty()) {
                throw new RuntimeException("No payment found for order: " + orderId);
            }

            Payment payment = payments.get(0);

            // OPTIMIZED: Enhanced customer data handling
            if (customerData != null) {
                String name = customerData.get("name");
                String email = customerData.get("email");
                String phone = customerData.get("phone");

                System.out.println("💾 Saving customer data for order: " + orderId);
                paymentService.saveCustomerData(name, email, phone);
            }

            // Update payment status immediately
            paymentService.updatePaymentStatus(payment.getTransactionId(), orderId);

            // OPTIMIZED: Enhanced async email sending with better error handling
            CompletableFuture.runAsync(() -> {
                try {
                    System.out.println("📧 Sending confirmation emails for order: " + orderId);
                    paymentService.sendConfirmationEmails(payment.getTransactionId(), orderId);
                    System.out.println("✅ Confirmation emails sent successfully");
                } catch (Exception e) {
                    System.err.println("❌ Error sending confirmation emails for order " + orderId + ": [REDACTED]");
                    // Could implement retry logic here
                }
            });

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Payment confirmed successfully");
            response.put("orderId", orderId);
            response.put("paymentMethod", payment.getPaymentMethod());
            response.put("transactionId", payment.getTransactionId());
            response.put("amount", payment.getAmount());

            System.out.println("✅ Payment confirmation complete for order: " + orderId);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            System.err.println("❌ Error confirming payment for order " + orderId + ": [REDACTED]");
            return ResponseEntity.badRequest().body("Error confirming payment: " + e.getMessage());
        }
    }

    /**
     * Mobile payment confirmation endpoint (legacy support)
     */
    @PostMapping("/{orderId}/confirmPayment/mobile")
    public ResponseEntity<?> confirmMobilePayment(@PathVariable Long orderId,
                                                  @RequestBody Map<String, Object> paymentData) {
        try {
            String transactionId = (String) paymentData.get("transactionId");
            String paymentMethod = (String) paymentData.get("paymentMethod");
            @SuppressWarnings("unchecked")
            Map<String, String> billingDetails = (Map<String, String>) paymentData.get("billingDetails");

            // Confirm mobile payment
            mobilePaymentService.confirmMobilePayment(transactionId, orderId, paymentMethod, billingDetails);

            // Save customer data if provided
            if (billingDetails != null) {
                paymentService.saveCustomerData(
                        billingDetails.get("name"),
                        billingDetails.get("email"),
                        billingDetails.get("phone")
                );
            }

            // Send confirmation emails async
            CompletableFuture.runAsync(() -> {
                try {
                    paymentService.sendConfirmationEmails(transactionId, orderId);
                } catch (Exception e) {
                    System.err.println("Error sending confirmation emails: [REDACTED]");
                }
            });

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Mobile payment confirmed successfully");
            response.put("orderId", orderId);
            response.put("paymentMethod", paymentMethod);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error confirming mobile payment: " + e.getMessage());
        }
    }

    // ========== HELPER METHODS ==========

    /**
     * Helper method to build available payment methods list
     */
    private Map<String, Object> getMethodsList(Map<String, Object> capabilities) {
        Map<String, Object> methods = new HashMap<>();

        // ExpressCheckout - Primary recommendation
        methods.put("express_checkout", Map.of(
                "available", true,
                "name", "Express Checkout",
                "description", "Apple Pay, Google Pay, Link, and more",
                "recommended", true
        ));

        // Always available
        methods.put("card", Map.of(
                "available", true,
                "name", "Credit/Debit Card",
                "description", "Pay with your credit or debit card"
        ));

        // Apple Pay
        methods.put("apple_pay", Map.of(
                "available", (Boolean) capabilities.get("supportsApplePay"),
                "name", "Apple Pay",
                "description", "Pay securely with Touch ID or Face ID"
        ));

        // Google Pay
        methods.put("google_pay", Map.of(
                "available", (Boolean) capabilities.get("supportsGooglePay"),
                "name", "Google Pay",
                "description", "Pay with your saved Google payment methods"
        ));

        return methods;
    }
}