package com.bistro_template_backend.services;

import com.bistro_template_backend.dto.PaymentRequest;
import com.bistro_template_backend.models.*;
import com.bistro_template_backend.repositories.OrderRepository;
import com.bistro_template_backend.repositories.PaymentRepository;
import com.stripe.Stripe;
import com.stripe.model.PaymentIntent;
import com.stripe.param.PaymentIntentCreateParams;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

@Service
public class MobilePaymentService {

    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;
    private final String stripeSecretKey;

    // Constructor injection to avoid circular dependencies
    public MobilePaymentService(PaymentRepository paymentRepository,
                                OrderRepository orderRepository,
                                @Value("${stripe.secretKey}") String stripeSecretKey) {
        this.paymentRepository = paymentRepository;
        this.orderRepository = orderRepository;
        this.stripeSecretKey = stripeSecretKey;
    }

    /**
     * Create a PaymentIntent for Apple Pay using Stripe
     */
    public Map<String, Object> createApplePayIntent(Order order, PaymentRequest request) {
        try {
            Stripe.apiKey = stripeSecretKey;

            long amountInCents = request.getAmount()
                    .multiply(new BigDecimal("100")).longValue();

            // FIXED: Use only automatic_payment_methods, remove addPaymentMethodType
            PaymentIntentCreateParams params = PaymentIntentCreateParams.builder()
                    .setAmount(amountInCents)
                    .setCurrency(request.getCurrency().toLowerCase())
                    .setDescription(request.getDescription())
                    .putMetadata("orderId", order.getId().toString())
                    .putMetadata("paymentMethod", "apple_pay")
                    // FIXED: Only use automatic payment methods for Apple Pay
                    .setAutomaticPaymentMethods(
                            PaymentIntentCreateParams.AutomaticPaymentMethods.builder()
                                    .setEnabled(true)
                                    .setAllowRedirects(PaymentIntentCreateParams.AutomaticPaymentMethods.AllowRedirects.NEVER)
                                    .build()
                    )
                    .build();

            PaymentIntent paymentIntent = PaymentIntent.create(params);

            // Create payment record
            Payment payment = new Payment();
            payment.setOrderId(order.getId());
            payment.setAmount(request.getAmount());
            payment.setPaymentMethod("APPLE_PAY");
            payment.setTransactionId(paymentIntent.getId());
            payment.setStatus(PaymentStatus.INITIATED);
            paymentRepository.save(payment);

            Map<String, Object> response = new HashMap<>();
            response.put("clientSecret", paymentIntent.getClientSecret());
            response.put("paymentIntentId", paymentIntent.getId());

            return response;
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Error creating Apple Pay PaymentIntent: " + e.getMessage(), e);
        }
    }

    /**
     * Create a PaymentIntent for Google Pay using Stripe
     */
    public Map<String, Object> createGooglePayIntent(Order order, PaymentRequest request) {
        try {
            Stripe.apiKey = stripeSecretKey;

            long amountInCents = request.getAmount()
                    .multiply(new BigDecimal("100")).longValue();

            // FIXED: Use only automatic_payment_methods, remove addPaymentMethodType
            PaymentIntentCreateParams params = PaymentIntentCreateParams.builder()
                    .setAmount(amountInCents)
                    .setCurrency(request.getCurrency().toLowerCase())
                    .setDescription(request.getDescription())
                    .putMetadata("orderId", order.getId().toString())
                    .putMetadata("paymentMethod", "google_pay")
                    // FIXED: Only use automatic payment methods for Google Pay
                    .setAutomaticPaymentMethods(
                            PaymentIntentCreateParams.AutomaticPaymentMethods.builder()
                                    .setEnabled(true)
                                    .setAllowRedirects(PaymentIntentCreateParams.AutomaticPaymentMethods.AllowRedirects.ALWAYS)
                                    .build()
                    )
                    .setSetupFutureUsage(PaymentIntentCreateParams.SetupFutureUsage.OFF_SESSION)
                    .setCaptureMethod(PaymentIntentCreateParams.CaptureMethod.AUTOMATIC)
                    .setConfirmationMethod(PaymentIntentCreateParams.ConfirmationMethod.AUTOMATIC)
                    .build();

            PaymentIntent paymentIntent = PaymentIntent.create(params);

            // Create payment record
            Payment payment = new Payment();
            payment.setOrderId(order.getId());
            payment.setAmount(request.getAmount());
            payment.setPaymentMethod("GOOGLE_PAY");
            payment.setTransactionId(paymentIntent.getId());
            payment.setStatus(PaymentStatus.INITIATED);
            paymentRepository.save(payment);

            Map<String, Object> response = new HashMap<>();
            response.put("clientSecret", paymentIntent.getClientSecret());
            response.put("paymentIntentId", paymentIntent.getId());

            return response;
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Error creating Google Pay PaymentIntent: " + e.getMessage(), e);
        }
    }

    /**
     * Validate mobile payment capabilities
     */
    public Map<String, Object> validateMobilePaymentCapabilities(String userAgent, String paymentMethod) {
        Map<String, Object> capabilities = new HashMap<>();

        boolean isIOS = userAgent != null && (userAgent.toLowerCase().contains("iphone") ||
                userAgent.toLowerCase().contains("ipad"));
        boolean isAndroid = userAgent != null && userAgent.toLowerCase().contains("android");
        boolean isMobile = isIOS || isAndroid;

        capabilities.put("isIOS", isIOS);
        capabilities.put("isAndroid", isAndroid);
        capabilities.put("isMobile", isMobile);

        // Apple Pay is primarily available on iOS devices and Safari on macOS
        boolean supportsApplePay = isIOS || (userAgent != null && userAgent.toLowerCase().contains("safari"));

        // Google Pay is available on Android and supported browsers
        boolean supportsGooglePay = isAndroid ||
                (userAgent != null && (userAgent.toLowerCase().contains("chrome") ||
                        userAgent.toLowerCase().contains("firefox") ||
                        userAgent.toLowerCase().contains("edge")));

        capabilities.put("supportsApplePay", supportsApplePay);
        capabilities.put("supportsGooglePay", supportsGooglePay);

        // Recommended payment method based on device
        String recommendedMethod = "card"; // default
        if (isIOS && "apple_pay".equals(paymentMethod)) {
            recommendedMethod = "apple_pay";
        } else if (isAndroid && "google_pay".equals(paymentMethod)) {
            recommendedMethod = "google_pay";
        }

        capabilities.put("recommendedMethod", recommendedMethod);

        return capabilities;
    }

    /**
     * Get payment method configuration for mobile devices
     */
    public Map<String, Object> getMobilePaymentConfig(String orderId, String paymentMethod) {
        Map<String, Object> config = new HashMap<>();

        // Basic configuration
        config.put("orderId", orderId);
        config.put("paymentMethod", paymentMethod);
        config.put("currency", "USD");
        config.put("country", "US");

        // Apple Pay specific configuration
        if ("apple_pay".equals(paymentMethod)) {
            Map<String, Object> applePayConfig = new HashMap<>();
            applePayConfig.put("merchantId", "merchant.com.bistrotemplate.payments");
            applePayConfig.put("merchantName", "Bistro Template");
            applePayConfig.put("supportedNetworks", Arrays.asList("visa", "mastercard", "amex", "discover"));
            applePayConfig.put("merchantCapabilities", Arrays.asList("supports3DS"));
            applePayConfig.put("requiredBillingContactFields", Arrays.asList("name", "email"));
            applePayConfig.put("requiredShippingContactFields", Arrays.asList());
            config.put("applePayConfig", applePayConfig);
        }

        // Google Pay specific configuration
        if ("google_pay".equals(paymentMethod)) {
            Map<String, Object> googlePayConfig = new HashMap<>();
            googlePayConfig.put("merchantId", "bistro-template-payments");
            googlePayConfig.put("merchantName", "Bistro Template");
            googlePayConfig.put("allowedCardNetworks", Arrays.asList("VISA", "MASTERCARD", "AMEX", "DISCOVER"));
            googlePayConfig.put("allowedCardAuthMethods", Arrays.asList("PAN_ONLY", "CRYPTOGRAM_3DS"));
            googlePayConfig.put("environment", "TEST"); // Change to "PRODUCTION" for live
            config.put("googlePayConfig", googlePayConfig);
        }

        return config;
    }

    /**
     * Process mobile payment confirmation
     */
    public void confirmMobilePayment(String transactionId, Long orderId, String paymentMethod,
                                     Map<String, String> billingDetails) {
        try {
            // Find the payment record
            Payment payment = paymentRepository.findByTransactionId(transactionId);
            if (payment == null) {
                throw new RuntimeException("Payment not found for transaction: " + transactionId);
            }

            // Update payment status
            payment.setStatus(PaymentStatus.PAID);
            paymentRepository.save(payment);

            // Update order status
            Order order = orderRepository.findById(orderId)
                    .orElseThrow(() -> new RuntimeException("Order not found: " + orderId));
            order.setPaymentStatus(PaymentStatus.PAID);
            orderRepository.save(order);

            // Log successful payment
            System.out.println("Mobile payment confirmed - Method: " + paymentMethod +
                    ", Order: " + orderId + ", Transaction: " + transactionId);

        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Error confirming mobile payment: " + e.getMessage(), e);
        }
    }
}