package com.bistro_template_backend.services;

import com.bistro_template_backend.dto.PaymentRequest;
import com.bistro_template_backend.models.*;
import com.bistro_template_backend.repositories.*;
import jakarta.mail.MessagingException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.stripe.Stripe;
import com.stripe.model.PaymentIntent;
import com.stripe.param.PaymentIntentCreateParams;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

@Service
@Slf4j
public class PaymentService {

    @Autowired
    private EmailService emailService;

    @Autowired
    PaymentRepository paymentRepository;

    @Autowired
    OrderItemRepository orderItemRepository;

    @Autowired
    OrderItemCustomizationRepository orderItemCustomizationRepository;

    @Autowired
    MenuItemRepository menuItemRepository;

    @Autowired
    CustomerRepository customerRepository;

    @Autowired
    CustomizationRepository customizationRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    WebSocketOrderService webSocketOrderService;

    @Autowired
    CognitoService cognitoService;

    @Autowired
    RewardsService rewardsService;

    // Read your Stripe secret key from application.yml or env var
    @Value("${stripe.secretKey}")
    private String stripeSecretKey;

    @Value("${spring.mail.username}")
    private String adminEmail;

    /**
     * Create a PaymentIntent in Stripe
     */
    public Map<String, Object> createStripePaymentIntent(Order order, PaymentRequest request) {
        try {
            // 1. Configure Stripe
            Stripe.apiKey = stripeSecretKey;

            // 2. Convert BigDecimal to long for Stripe (in cents)
            long amountInCents = request.getAmount()
                    .multiply(new BigDecimal("100")).longValue();

            // 3. Create PaymentIntent
            PaymentIntentCreateParams params =
                    PaymentIntentCreateParams.builder()
                            .setAmount(amountInCents)
                            .setCurrency(request.getCurrency())
                            .setDescription(request.getDescription())
                            // Example: pass orderId in metadata
                            .putMetadata("orderId", order.getId().toString())
                            .build();

            PaymentIntent paymentIntent = PaymentIntent.create(params);

            // 4. Create a Payment record (status = INITIATED)
            Payment payment = new Payment();
            payment.setOrderId(order.getId());
            payment.setAmount(request.getAmount());
            payment.setPaymentMethod("STRIPE");
            payment.setTransactionId(paymentIntent.getId()); // store PaymentIntent ID
            payment.setStatus(PaymentStatus.INITIATED);
            paymentRepository.save(payment);

            // 5. Return clientSecret to frontend
            Map<String, Object> response = new HashMap<>();
            response.put("clientSecret", paymentIntent.getClientSecret());
            response.put("paymentIntentId", paymentIntent.getId());
            return response;
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Error creating Stripe PaymentIntent", e);
        }
    }

    // Quick status update - returns immediately
    public void updatePaymentStatus(String transactionId, Long orderId) {
        // Fetch the payment and order
        Payment payment = paymentRepository.findByTransactionId(transactionId);
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found"));

        // Update payment status
        payment.setStatus(PaymentStatus.PAID);
        paymentRepository.save(payment);

        // Update order status
        order.setPaymentStatus(PaymentStatus.PAID);
        orderRepository.save(order);

        // Award points if customer is authenticated
        awardPointsForCompletedOrder(orderId);

        // **NEW: Send WebSocket notification for new paid order**
        webSocketOrderService.notifyNewOrder(order);

        // Update customer stats if order has customer email
        if (StringUtils.hasText(order.getCustomerEmail())) {
            Optional<Customer> customerOpt = customerRepository.findByEmail(order.getCustomerEmail());

            if (customerOpt.isPresent()) {
                Customer customer = customerOpt.get();

                // Update last order date
                customer.setLastOrderDate(LocalDateTime.now());

                // Increment total orders
                Integer totalOrders = customer.getTotalOrders();
                if (totalOrders == null) {
                    totalOrders = 0;
                }
                customer.setTotalOrders(totalOrders + 1);

                customerRepository.save(customer);
            }
        }
    }

    /**
     * Award points for completed order if customer is authenticated
     */
    private void awardPointsForCompletedOrder(Long orderId) {
        try {
            // Check if there's an authenticated customer
            String cognitoUserId = cognitoService.getCurrentCognitoUserId();
            if (cognitoUserId != null) {
                // Get customer account
                CustomerAccount account = cognitoService.getCurrentCustomerAccount();
                if (account != null) {
                    // Award points asynchronously to avoid blocking payment completion
                    CompletableFuture.runAsync(() -> {
                        try {
                            rewardsService.awardPointsForOrder(account.getId(), orderId);
                            log.info("Points awarded for order {} to customer {}", orderId, account.getEmail());
                        } catch (Exception e) {
                            log.error("Error awarding points for order {}: {}", orderId, e.getMessage(), e);
                        }
                    });
                }
            }
        } catch (Exception e) {
            log.warn("Could not award points for order {} - customer may not be authenticated: {}", orderId, e.getMessage());
        }
    }

    // Email sending - runs asynchronously
    public void sendConfirmationEmails(String transactionId, Long orderId) throws MessagingException {
        // Fetch the payment and order again (to ensure we have fresh data)
        Payment payment = paymentRepository.findByTransactionId(transactionId);
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found"));

        // All the existing email creation and sending code
        // [the rest of your existing email code]
        // Fetch all OrderItems for this Order (unchanged)
        List<OrderItem> orderItems = orderItemRepository.findByOrderId(order.getId());

        // Base time and calculations (unchanged)
        LocalDateTime orderTime = order.getOrderDate();
        int baseTime = 15; // 15 minutes base preparation time
        int extraTime = 0;

        // Count total items and customizations (unchanged)
        int totalItems = orderItems.stream().mapToInt(OrderItem::getQuantity).sum();
        int totalCustomizations = orderItems.stream()
                .mapToInt(item -> orderItemCustomizationRepository.findByOrderItemId(item.getId()).size())
                .sum();

        // Add extra time based on order size (unchanged)
        if (totalItems > 5) {
            extraTime += 10;
        }
        if (totalItems > 10) {
            extraTime += 20;
        }

        // Add extra time for each customization (2 min per customization) (unchanged)
        extraTime += totalCustomizations * 2;

        // Add extra time for peak hours (12 PM - 2 PM, 6 PM - 8 PM) (unchanged)
        int orderHour = orderTime.getHour();
        if ((orderHour >= 12 && orderHour < 14) || (orderHour >= 18 && orderHour < 20)) {
            extraTime += 20;
        }

        // Calculate pickup time range (add breathing room of 10 minutes) (unchanged)
        LocalDateTime estimatedPickupMin = orderTime.plusMinutes(baseTime + extraTime);
        LocalDateTime estimatedPickupMax = estimatedPickupMin.plusMinutes(10);

        // Format pickup time range (unchanged)
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("hh:mm a");
        String formattedPickupRange = estimatedPickupMin.format(formatter) + " - " + estimatedPickupMax.format(formatter);

        // Build HTML email content for order details - IMPROVED VERSION
        String customerEmail = order.getCustomerEmail();
        String customerSubject = "Order Confirmation: Your Your Restaurant Name Order #" + order.getId();
        String adminSubject = "New Order #" + order.getId() + " - Ready for Preparation";

        // Customer HTML email - IMPROVED VERSION
        String customerHtmlBody = "<!DOCTYPE html>\n" +
                "<html>\n" +
                "<head>\n" +
                "    <meta charset='UTF-8'>\n" +
                "    <meta name='viewport' content='width=device-width, initial-scale=1.0'>\n" +
                "    <title>Order Confirmation</title>\n" +
                "</head>\n" +
                "<body style='font-family: Arial, sans-serif; margin: 0; padding: 0; background-color: #f8f8f8; color: #333333;'>\n" +
                "    <table role='presentation' cellspacing='0' cellpadding='0' border='0' align='center' width='100%' style='max-width: 600px; margin: 0 auto; background-color: #ffffff; border-radius: 8px; box-shadow: 0 2px 5px rgba(0,0,0,0.1); overflow: hidden;'>\n" +
                "        <!-- HEADER -->\n" +
                "        <tr>\n" +
                "            <td style='background-color: #c6632c; padding: 30px 40px; text-align: center;'>\n" +
                "                <h1 style='color: #ffffff; margin: 0; font-size: 28px;'>Order Confirmation</h1>\n" +
                "            </td>\n" +
                "        </tr>\n" +
                "        <!-- MAIN CONTENT -->\n" +
                "        <tr>\n" +
                "            <td style='padding: 40px 40px 20px 40px;'>\n" +
                "                <p style='margin-top: 0; font-size: 16px;'>Dear " + order.getCustomerName() + ",</p>\n" +
                "                <p style='font-size: 16px;'>Thank you for ordering from Your Restaurant Name! Your order has been received and will be ready for pickup soon.</p>\n" +
                "                \n" +
                "                <!-- ORDER DETAILS HEADER -->\n" +
                "                <table role='presentation' width='100%' cellspacing='0' cellpadding='0' border='0' style='margin: 30px 0 10px 0;'>\n" +
                "                    <tr>\n" +
                "                        <td style='background-color: #f5f5f5; padding: 12px 15px; border-top-left-radius: 6px; border-top-right-radius: 6px; border-bottom: 2px solid #c6632c;'>\n" +
                "                            <h2 style='margin: 0; color: #c6632c; font-size: 18px;'>Order Summary</h2>\n" +
                "                        </td>\n" +
                "                    </tr>\n" +
                "                </table>\n" +
                "                \n" +
                "                <!-- ORDER INFO -->\n" +
                "                <table role='presentation' width='100%' cellspacing='0' cellpadding='0' border='0' style='margin-bottom: 25px; border-collapse: separate; border-spacing: 0;'>\n" +
                "                    <tr>\n" +
                "                        <td style='padding: 12px 15px; border-bottom: 1px solid #eeeeee; font-weight: bold; width: 40%;'>Order Number:</td>\n" +
                "                        <td style='padding: 12px 15px; border-bottom: 1px solid #eeeeee;'>#" + order.getId() + "</td>\n" +
                "                    </tr>\n" +
                "                    <tr>\n" +
                "                        <td style='padding: 12px 15px; border-bottom: 1px solid #eeeeee; font-weight: bold;'>Order Date:</td>\n" +
                "                        <td style='padding: 12px 15px; border-bottom: 1px solid #eeeeee;'>" +
                order.getOrderDate().format(DateTimeFormatter.ofPattern("MMMM d, yyyy 'at' hh:mm a")) + "</td>\n" +
                "                    </tr>\n" +
                "                    <tr>\n" +
                "                        <td style='padding: 12px 15px; border-bottom: 1px solid #eeeeee; font-weight: bold;'>Estimated Pickup:</td>\n" +
                "                        <td style='padding: 12px 15px; border-bottom: 1px solid #eeeeee; font-weight: bold; color: #c6632c;'>" +
                formattedPickupRange + "</td>\n" +
                "                    </tr>\n";

        // Add special notes if present
        if (order.getSpecialNotes() != null && !order.getSpecialNotes().isEmpty()) {
            customerHtmlBody += "                    <tr>\n" +
                    "                        <td style='padding: 12px 15px; border-bottom: 1px solid #eeeeee; font-weight: bold;'>Special Notes:</td>\n" +
                    "                        <td style='padding: 12px 15px; border-bottom: 1px solid #eeeeee; font-style: italic;'>" +
                    order.getSpecialNotes() + "</td>\n" +
                    "                    </tr>\n";
        }

        customerHtmlBody += "                </table>\n" +
                "                \n" +
                "                <!-- ORDER ITEMS -->\n" +
                "                <table role='presentation' width='100%' cellspacing='0' cellpadding='0' border='0' style='margin-bottom: 25px; border-collapse: separate; border-spacing: 0;'>\n" +
                "                    <tr>\n" +
                "                        <td colspan='3' style='background-color: #f5f5f5; padding: 12px 15px; border-top-left-radius: 6px; border-top-right-radius: 6px; border-bottom: 2px solid #c6632c;'>\n" +
                "                            <h3 style='margin: 0; color: #333333; font-size: 16px;'>Items</h3>\n" +
                "                        </td>\n" +
                "                    </tr>\n";

        // Separate regular items from reward items
        List<OrderItem> regularItems = orderItems.stream().filter(item -> !item.isRewardItem()).toList();
        List<OrderItem> rewardItems = orderItems.stream().filter(OrderItem::isRewardItem).toList();

        // Add regular item details
        for (OrderItem item : regularItems) {
            MenuItem menuItem = menuItemRepository.findById(item.getMenuItemId())
                    .orElseThrow(() -> new RuntimeException("Menu item not found"));

            customerHtmlBody += "                    <tr>\n" +
                    "                        <td style='padding: 15px; border-bottom: 1px solid #eeeeee; width: 60%;'>\n" +
                    "                            <span style='font-weight: bold; font-size: 16px;'>" + menuItem.getName() + "</span>\n" +
                    "                        </td>\n" +
                    "                        <td style='padding: 15px; border-bottom: 1px solid #eeeeee; text-align: center;'>\n" +
                    "                            x" + item.getQuantity() + "\n" +
                    "                        </td>\n" +
                    "                        <td style='padding: 15px; border-bottom: 1px solid #eeeeee; text-align: right;'>\n" +
                    "                            $" + String.format("%.2f", menuItem.getPrice()) + " each\n" +
                    "                        </td>\n" +
                    "                    </tr>\n";

            // Fetch and append customizations for this OrderItem
            List<OrderItemCustomization> selectedCustomizations = orderItemCustomizationRepository.findByOrderItemId(item.getId());
            if (!selectedCustomizations.isEmpty()) {
                customerHtmlBody += "                    <tr>\n" +
                        "                        <td colspan='3' style='padding: 0 15px 15px 30px; border-bottom: 1px solid #eeeeee;'>\n";

                for (OrderItemCustomization customization : selectedCustomizations) {
                    Customization customizationDetails = customization.getCustomization();
                    customerHtmlBody += "                            <div style='color: #666666; font-size: 14px; margin-bottom: 5px;'>\n" +
                            "                                • " + customizationDetails.getName();

                    if (customizationDetails.getPrice().compareTo(BigDecimal.ZERO) > 0) {
                        customerHtmlBody += " <span style='color: #c6632c;'>(+ $" +
                                String.format("%.2f", customizationDetails.getPrice()) + ")</span>";
                    }

                    customerHtmlBody += "\n                            </div>\n";
                }

                customerHtmlBody += "                        </td>\n" +
                        "                    </tr>\n";
            }
        }

        customerHtmlBody += "                </table>\n";

        // Add reward items section if there are any
        if (!rewardItems.isEmpty()) {
            customerHtmlBody += "                \n" +
                    "                <!-- REWARD ITEMS -->\n" +
                    "                <table role='presentation' width='100%' cellspacing='0' cellpadding='0' border='0' style='margin-bottom: 25px; border-collapse: separate; border-spacing: 0;'>\n" +
                    "                    <tr>\n" +
                    "                        <td colspan='3' style='background-color: #28a745; padding: 12px 15px; border-top-left-radius: 6px; border-top-right-radius: 6px; border-bottom: 2px solid #1e7e34;'>\n" +
                    "                            <h3 style='margin: 0; color: #ffffff; font-size: 16px; display: flex; align-items: center;'>\n" +
                    "                                🎁 Reward Items (FREE!)\n" +
                    "                            </h3>\n" +
                    "                        </td>\n" +
                    "                    </tr>\n";

            for (OrderItem item : rewardItems) {
                MenuItem menuItem = menuItemRepository.findById(item.getMenuItemId())
                        .orElseThrow(() -> new RuntimeException("Menu item not found"));

                customerHtmlBody += "                    <tr>\n" +
                        "                        <td style='padding: 15px; border-bottom: 1px solid #eeeeee; width: 60%; background-color: #f8fff9;'>\n" +
                        "                            <span style='font-weight: bold; font-size: 16px; color: #28a745;'>" + menuItem.getName() + "</span>\n" +
                        "                            <br><span style='font-size: 12px; color: #6c757d; font-style: italic;'>Redeemed with points</span>\n" +
                        "                        </td>\n" +
                        "                        <td style='padding: 15px; border-bottom: 1px solid #eeeeee; text-align: center; background-color: #f8fff9;'>\n" +
                        "                            x" + item.getQuantity() + "\n" +
                        "                        </td>\n" +
                        "                        <td style='padding: 15px; border-bottom: 1px solid #eeeeee; text-align: right; background-color: #f8fff9; color: #28a745; font-weight: bold;'>\n" +
                        "                            FREE!\n" +
                        "                        </td>\n" +
                        "                    </tr>\n";

                // Fetch and append customizations for reward items
                List<OrderItemCustomization> selectedCustomizations = orderItemCustomizationRepository.findByOrderItemId(item.getId());
                if (!selectedCustomizations.isEmpty()) {
                    customerHtmlBody += "                    <tr>\n" +
                            "                        <td colspan='3' style='padding: 0 15px 15px 30px; border-bottom: 1px solid #eeeeee; background-color: #f8fff9;'>\n";

                    for (OrderItemCustomization customization : selectedCustomizations) {
                        Customization customizationDetails = customization.getCustomization();
                        customerHtmlBody += "                            <div style='color: #666666; font-size: 14px; margin-bottom: 5px;'>\n" +
                                "                                • " + customizationDetails.getName();

                        if (customizationDetails.getPrice().compareTo(BigDecimal.ZERO) > 0) {
                            customerHtmlBody += " <span style='color: #28a745;'>(+ $" +
                                    String.format("%.2f", customizationDetails.getPrice()) + ")</span>";
                        }

                        customerHtmlBody += "\n                            </div>\n";
                    }

                    customerHtmlBody += "                        </td>\n" +
                            "                    </tr>\n";
                }
            }
            customerHtmlBody += "                </table>\n";
        }

        // Add order totals
        customerHtmlBody += "                \n" +
                "                <!-- ORDER TOTALS -->\n" +
                "                <table role='presentation' width='100%' cellspacing='0' cellpadding='0' border='0' style='margin-bottom: 30px; border-collapse: separate; border-spacing: 0;'>\n" +
                "                    <tr>\n" +
                "                        <td style='padding: 12px 15px; text-align: right; font-weight: bold;'>Subtotal:</td>\n" +
                "                        <td style='padding: 12px 15px; text-align: right; width: 100px;'>$" +
                String.format("%.2f", order.getSubTotal()) + "</td>\n" +
                "                    </tr>\n" +
                "                    <tr>\n" +
                "                        <td style='padding: 12px 15px; text-align: right; font-weight: bold;'>Tax:</td>\n" +
                "                        <td style='padding: 12px 15px; text-align: right;'>$" +
                String.format("%.2f", order.getTax()) + "</td>\n" +
                "                    </tr>\n" +
                "                    <tr>\n" +
                "                        <td style='padding: 12px 15px; text-align: right; font-weight: bold;'>Service Fee:</td>\n" +
                "                        <td style='padding: 12px 15px; text-align: right;'>$" +
                String.format("%.2f", order.getServiceFee()) + "</td>\n" +
                "                    </tr>\n" +
                "                    <tr>\n" +
                "                        <td style='padding: 15px; text-align: right; font-weight: bold; font-size: 18px; border-top: 2px solid #eeeeee;'>Total:</td>\n" +
                "                        <td style='padding: 15px; text-align: right; font-weight: bold; font-size: 18px; border-top: 2px solid #eeeeee; color: #c6632c;'>$" +
                String.format("%.2f", order.getTotalAmount()) + "</td>\n" +
                "                    </tr>\n" +
                "                </table>\n" +
                "                \n" +
                "                <p style='font-size: 16px;'>We're preparing your order with care and it will be ready for pickup at our restaurant soon.</p>\n" +
                "                <p style='font-size: 16px;'>If you have any questions, please contact us at <a href='tel:2812420190' style='color: #c6632c; text-decoration: none;'>(281) 242-0190</a>.</p>\n" +
                "                <p style='font-size: 16px;'>Thank you for choosing Your Restaurant Name!</p>\n" +
                "            </td>\n" +
                "        </tr>\n" +
                "        <!-- FOOTER -->\n" +
                "        <tr>\n" +
                "            <td style='padding: 20px; background-color: #f5f5f5; text-align: center; font-size: 14px; color: #666666;'>\n" +
                "                <p style='margin: 0 0 10px 0;'><strong>Your Restaurant Name</strong><br>14019 Southwest Fwy, Ste 204, Sugar Land, TX 77478</p>\n" +
                "                <p style='margin: 0; font-size: 12px;'>© 2025 Your Restaurant Name. All rights reserved.</p>\n" +
                "            </td>\n" +
                "        </tr>\n" +
                "    </table>\n" +
                "</body>\n" +
                "</html>";

        // Admin HTML email - IMPROVED VERSION
        String adminHtmlBody = "<!DOCTYPE html>\n" +
                "<html>\n" +
                "<head>\n" +
                "    <meta charset='UTF-8'>\n" +
                "    <meta name='viewport' content='width=device-width, initial-scale=1.0'>\n" +
                "    <title>New Order Alert</title>\n" +
                "</head>\n" +
                "<body style='font-family: Arial, sans-serif; margin: 0; padding: 0; background-color: #f8f8f8; color: #333333;'>\n" +
                "    <table role='presentation' cellspacing='0' cellpadding='0' border='0' align='center' width='100%' style='max-width: 600px; margin: 0 auto; background-color: #ffffff; border-radius: 8px; box-shadow: 0 2px 5px rgba(0,0,0,0.1); overflow: hidden;'>\n" +
                "        <!-- HEADER -->\n" +
                "        <tr>\n" +
                "            <td style='background-color: #d9534f; padding: 30px 40px; text-align: center;'>\n" +
                "                <h1 style='color: #ffffff; margin: 0; font-size: 28px;'>New Order Alert</h1>\n" +
                "            </td>\n" +
                "        </tr>\n" +
                "        <!-- MAIN CONTENT -->\n" +
                "        <tr>\n" +
                "            <td style='padding: 40px 40px 20px 40px;'>\n" +
                "                <div style='background-color: #fff3cd; border-left: 4px solid #ffc107; padding: 15px; margin-bottom: 25px;'>\n" +
                "                    <h2 style='margin-top: 0; margin-bottom: 10px; color: #856404; font-size: 18px;'>Action Required</h2>\n" +
                "                    <p style='margin-bottom: 0; font-size: 16px;'>A new order has been received and needs to be prepared.</p>\n" +
                "                </div>\n" +
                "                \n" +
                "                <!-- ORDER DETAILS HEADER -->\n" +
                "                <table role='presentation' width='100%' cellspacing='0' cellpadding='0' border='0' style='margin: 20px 0 10px 0;'>\n" +
                "                    <tr>\n" +
                "                        <td style='background-color: #f5f5f5; padding: 12px 15px; border-top-left-radius: 6px; border-top-right-radius: 6px; border-bottom: 2px solid #d9534f;'>\n" +
                "                            <h2 style='margin: 0; color: #d9534f; font-size: 18px;'>Order Information</h2>\n" +
                "                        </td>\n" +
                "                    </tr>\n" +
                "                </table>\n" +
                "                \n" +
                "                <!-- ORDER INFO -->\n" +
                "                <table role='presentation' width='100%' cellspacing='0' cellpadding='0' border='0' style='margin-bottom: 25px; border-collapse: separate; border-spacing: 0;'>\n" +
                "                    <tr>\n" +
                "                        <td style='padding: 12px 15px; border-bottom: 1px solid #eeeeee; font-weight: bold; width: 40%;'>Order Number:</td>\n" +
                "                        <td style='padding: 12px 15px; border-bottom: 1px solid #eeeeee; font-weight: bold;'>#" + order.getId() + "</td>\n" +
                "                    </tr>\n" +
                "                    <tr>\n" +
                "                        <td style='padding: 12px 15px; border-bottom: 1px solid #eeeeee; font-weight: bold;'>Customer:</td>\n" +
                "                        <td style='padding: 12px 15px; border-bottom: 1px solid #eeeeee;'>" +
                order.getCustomerName() + " (" + order.getCustomerEmail() + ")</td>\n" +
                "                    </tr>\n" +
                "                    <tr>\n" +
                "                        <td style='padding: 12px 15px; border-bottom: 1px solid #eeeeee; font-weight: bold;'>Order Date:</td>\n" +
                "                        <td style='padding: 12px 15px; border-bottom: 1px solid #eeeeee;'>" +
                order.getOrderDate().format(DateTimeFormatter.ofPattern("MMMM d, yyyy 'at' hh:mm a")) + "</td>\n" +
                "                    </tr>\n" +
                "                    <tr>\n" +
                "                        <td style='padding: 12px 15px; border-bottom: 1px solid #eeeeee; font-weight: bold;'>Target Pickup Time:</td>\n" +
                "                        <td style='padding: 12px 15px; border-bottom: 1px solid #eeeeee; font-weight: bold; color: #d9534f;'>" +
                formattedPickupRange + "</td>\n" +
                "                    </tr>\n";

        // Add special notes if present
        if (order.getSpecialNotes() != null && !order.getSpecialNotes().isEmpty()) {
            adminHtmlBody += "                    <tr>\n" +
                    "                        <td style='padding: 12px 15px; border-bottom: 1px solid #eeeeee; font-weight: bold;'>Special Notes:</td>\n" +
                    "                        <td style='padding: 12px 15px; border-bottom: 1px solid #eeeeee; background-color: #f8f9fa; font-weight: bold;'>" +
                    order.getSpecialNotes() + "</td>\n" +
                    "                    </tr>\n";
        }

        adminHtmlBody += "                </table>\n" +
                "                \n" +
                "                <!-- ORDER ITEMS -->\n" +
                "                <table role='presentation' width='100%' cellspacing='0' cellpadding='0' border='0' style='margin-bottom: 25px; border-collapse: separate; border-spacing: 0;'>\n" +
                "                    <tr>\n" +
                "                        <td colspan='3' style='background-color: #f5f5f5; padding: 12px 15px; border-top-left-radius: 6px; border-top-right-radius: 6px; border-bottom: 2px solid #d9534f;'>\n" +
                "                            <h3 style='margin: 0; color: #333333; font-size: 16px;'>Order Items</h3>\n" +
                "                        </td>\n" +
                "                    </tr>\n";

        // Add regular item details for admin
        for (OrderItem item : regularItems) {
            MenuItem menuItem = menuItemRepository.findById(item.getMenuItemId())
                    .orElseThrow(() -> new RuntimeException("Menu item not found"));

            adminHtmlBody += "                    <tr>\n" +
                    "                        <td style='padding: 15px; border-bottom: 1px solid #eeeeee; width: 60%; font-weight: bold;'>\n" +
                    "                            <span style='font-size: 16px;'>" + menuItem.getName() + "</span>\n" +
                    "                        </td>\n" +
                    "                        <td style='padding: 15px; border-bottom: 1px solid #eeeeee; text-align: center; font-weight: bold;'>\n" +
                    "                            x" + item.getQuantity() + "\n" +
                    "                        </td>\n" +
                    "                        <td style='padding: 15px; border-bottom: 1px solid #eeeeee; text-align: right;'>\n" +
                    "                            $" + String.format("%.2f", menuItem.getPrice()) + " each\n" +
                    "                        </td>\n" +
                    "                    </tr>\n";

            // Fetch and append customizations for this OrderItem
            List<OrderItemCustomization> selectedCustomizations = orderItemCustomizationRepository.findByOrderItemId(item.getId());
            if (!selectedCustomizations.isEmpty()) {
                adminHtmlBody += "                    <tr>\n" +
                        "                        <td colspan='3' style='padding: 0 15px 15px 30px; border-bottom: 1px solid #eeeeee;'>\n";

                for (OrderItemCustomization customization : selectedCustomizations) {
                    Customization customizationDetails = customization.getCustomization();
                    adminHtmlBody += "                            <div style='color: #666666; font-size: 14px; margin-bottom: 5px;'>\n" +
                            "                                • " + customizationDetails.getName();

                    if (customizationDetails.getPrice().compareTo(BigDecimal.ZERO) > 0) {
                        adminHtmlBody += " <span style='color: #d9534f;'>(+ $" +
                                String.format("%.2f", customizationDetails.getPrice()) + ")</span>";
                    }

                    adminHtmlBody += "\n                            </div>\n";
                }

                adminHtmlBody += "                        </td>\n" +
                        "                    </tr>\n";
            }
        }

        adminHtmlBody += "                </table>\n";

        // Add reward items section for admin if there are any
        if (!rewardItems.isEmpty()) {
            adminHtmlBody += "                \n" +
                    "                <!-- REWARD ITEMS -->\n" +
                    "                <table role='presentation' width='100%' cellspacing='0' cellpadding='0' border='0' style='margin-bottom: 25px; border-collapse: separate; border-spacing: 0;'>\n" +
                    "                    <tr>\n" +
                    "                        <td colspan='3' style='background-color: #28a745; padding: 12px 15px; border-top-left-radius: 6px; border-top-right-radius: 6px; border-bottom: 2px solid #1e7e34;'>\n" +
                    "                            <h3 style='margin: 0; color: #ffffff; font-size: 16px;'>🎁 Reward Items (FREE!)</h3>\n" +
                    "                        </td>\n" +
                    "                    </tr>\n";

            for (OrderItem item : rewardItems) {
                MenuItem menuItem = menuItemRepository.findById(item.getMenuItemId())
                        .orElseThrow(() -> new RuntimeException("Menu item not found"));

                adminHtmlBody += "                    <tr>\n" +
                        "                        <td style='padding: 15px; border-bottom: 1px solid #eeeeee; width: 60%; font-weight: bold; background-color: #f8fff9;'>\n" +
                        "                            <span style='font-size: 16px; color: #28a745;'>" + menuItem.getName() + "</span>\n" +
                        "                            <br><span style='font-size: 12px; color: #6c757d; font-style: italic; font-weight: normal;'>Redeemed with points</span>\n" +
                        "                        </td>\n" +
                        "                        <td style='padding: 15px; border-bottom: 1px solid #eeeeee; text-align: center; font-weight: bold; background-color: #f8fff9;'>\n" +
                        "                            x" + item.getQuantity() + "\n" +
                        "                        </td>\n" +
                        "                        <td style='padding: 15px; border-bottom: 1px solid #eeeeee; text-align: right; background-color: #f8fff9; color: #28a745; font-weight: bold;'>\n" +
                        "                            FREE!\n" +
                        "                        </td>\n" +
                        "                    </tr>\n";

                // Fetch and append customizations for reward items
                List<OrderItemCustomization> selectedCustomizations = orderItemCustomizationRepository.findByOrderItemId(item.getId());
                if (!selectedCustomizations.isEmpty()) {
                    adminHtmlBody += "                    <tr>\n" +
                            "                        <td colspan='3' style='padding: 0 15px 15px 30px; border-bottom: 1px solid #eeeeee; background-color: #f8fff9;'>\n";

                    for (OrderItemCustomization customization : selectedCustomizations) {
                        Customization customizationDetails = customization.getCustomization();
                        adminHtmlBody += "                            <div style='color: #666666; font-size: 14px; margin-bottom: 5px;'>\n" +
                                "                                • " + customizationDetails.getName();

                        if (customizationDetails.getPrice().compareTo(BigDecimal.ZERO) > 0) {
                            adminHtmlBody += " <span style='color: #28a745;'>(+ $" +
                                    String.format("%.2f", customizationDetails.getPrice()) + ")</span>";
                        }

                        adminHtmlBody += "\n                            </div>\n";
                    }

                    adminHtmlBody += "                        </td>\n" +
                            "                    </tr>\n";
                }
            }
            adminHtmlBody += "                </table>\n";
        }

        // Add order totals
        adminHtmlBody += "                \n" +
                "                <!-- ORDER TOTALS -->\n" +
                "                <table role='presentation' width='100%' cellspacing='0' cellpadding='0' border='0' style='margin-bottom: 30px; border-collapse: separate; border-spacing: 0;'>\n" +
                "                    <tr>\n" +
                "                        <td style='padding: 12px 15px; text-align: right; font-weight: bold;'>Subtotal:</td>\n" +
                "                        <td style='padding: 12px 15px; text-align: right; width: 100px;'>$" +
                String.format("%.2f", order.getSubTotal()) + "</td>\n" +
                "                    </tr>\n" +
                "                    <tr>\n" +
                "                        <td style='padding: 12px 15px; text-align: right; font-weight: bold;'>Tax:</td>\n" +
                "                        <td style='padding: 12px 15px; text-align: right;'>$" +
                String.format("%.2f", order.getTax()) + "</td>\n" +
                "                    </tr>\n" +
                "                    <tr>\n" +
                "                        <td style='padding: 12px 15px; text-align: right; font-weight: bold;'>Service Fee:</td>\n" +
                "                        <td style='padding: 12px 15px; text-align: right;'>$" +
                String.format("%.2f", order.getServiceFee()) + "</td>\n" +
                "                    </tr>\n" +
                "                    <tr>\n" +
                "                        <td style='padding: 15px; text-align: right; font-weight: bold; font-size: 18px; border-top: 2px solid #eeeeee;'>Total:</td>\n" +
                "                        <td style='padding: 15px; text-align: right; font-weight: bold; font-size: 18px; border-top: 2px solid #eeeeee; color: #d9534f;'>$" +
                String.format("%.2f", order.getTotalAmount()) + "</td>\n" +
                "                    </tr>\n" +
                "                </table>\n" +
                "                \n" +
                "                <div style='background-color: #f8d7da; border-left: 4px solid #d9534f; padding: 15px; margin-bottom: 25px;'>\n" +
                "                    <p style='margin: 0; font-weight: bold; font-size: 16px;'>Please prepare this order promptly to meet the target pickup time.</p>\n" +
                "                </div>\n" +
                "            </td>\n" +
                "        </tr>\n" +
                "        <!-- FOOTER -->\n" +
                "        <tr>\n" +
                "            <td style='padding: 20px; background-color: #f5f5f5; text-align: center; font-size: 14px; color: #666666;'>\n" +
                "                <p style='margin: 0 0 10px 0;'>This is an automated system notification.</p>\n" +
                "                <p style='margin: 0; font-size: 12px;'>© 2025 Your Restaurant Name. All rights reserved.</p>\n" +
                "            </td>\n" +
                "        </tr>\n" +
                "    </table>\n" +
                "</body>\n" +
                "</html>";

        // Send HTML emails
        try {
            emailService.sendHtmlEmail(customerEmail, customerSubject, customerHtmlBody);
            emailService.sendHtmlEmail(adminEmail, adminSubject, adminHtmlBody);
        } catch (MessagingException e) {
            // Log the error (e.g., using SLF4J or similar)
            System.err.println("Failed to send email: " + e.getMessage());
        }
    }

    // src/main/java/com/bistro_template_backend/services/PaymentService.java

    // Add this method to the PaymentService class
    public void saveCustomerData(String name, String email, String phone) {
        if (email == null || email.isEmpty()) {
            return; // Cannot save customer without email
        }

        // Check if customer already exists
        Optional<Customer> existingCustomer = customerRepository.findByEmail(email);

        if (existingCustomer.isPresent()) {
            // Update existing customer
            Customer customer = existingCustomer.get();
            // Only update name and phone if provided
            if (name != null && !name.isEmpty()) {
                customer.setFullName(name);
            }
            if (phone != null && !phone.isEmpty()) {
                customer.setPhone(phone);
            }
            customerRepository.save(customer);
        } else {
            // Create new customer
            Customer customer = new Customer();
            customer.setEmail(email);
            customer.setFullName(name);
            customer.setPhone(phone);
            customer.setCreatedAt(LocalDateTime.now());
            customer.setTotalOrders(0); // Initialize with zero orders
            customerRepository.save(customer);
        }
    }
    /**
     * Example: Create or get a PayPal order/approval link
     */
//    public Map<String, Object> createPayPalOrder(Order order, PaymentRequest request) {
//        // 1. Use PayPal SDK to create an order. For example:
//        //    - Build a Payment object in PayPal.
//        //    - Return the approval link to the frontend.
//        // This code is pseudo-code. Real usage requires PayPal OAuth or SDK calls.
//        Map<String, Object> result = new HashMap<>();
//        try {
//            // Pseudo:
//            // PayPalClient client = new PayPalClient("clientId", "secret");
//            // Payment payPalPayment = new Payment();
//            // payPalPayment.setIntent("sale");
//            // ...
//            // Approval URL -> user is redirected
//            String approvalUrl = "https://www.sandbox.paypal.com/checkoutnow?token=EXAMPLE123";
//
//            // 2. Save a Payment record in DB
//            Payment payment = new Payment();
//            payment.setOrderId(order.getId());
//            payment.setAmount(request.getAmount());
//            payment.setPaymentMethod("PAYPAL");
//            payment.setTransactionId("PAYPAL_TEMP_ID"); // or the token
//            payment.setStatus(PaymentStatus.INITIATED);
//            paymentRepository.save(payment);
//
//            // 3. Return the link
//            result.put("approvalLink", approvalUrl);
//            return result;
//        } catch (Exception e) {
//            e.printStackTrace();
//            throw new RuntimeException("Error creating PayPal order", e);
//        }
//    }

    // Additional methods for confirming payments, webhooks, etc.
}