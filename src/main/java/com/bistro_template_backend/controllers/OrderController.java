package com.bistro_template_backend.controllers;

import com.bistro_template_backend.dto.CreateOrderRequest;
import com.bistro_template_backend.models.*;
import com.bistro_template_backend.repositories.CustomizationRepository;
import com.bistro_template_backend.repositories.OrderItemCustomizationRepository;
import com.bistro_template_backend.repositories.OrderItemRepository;
import com.bistro_template_backend.repositories.OrderRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/orders")
public class OrderController {
    private static final BigDecimal TAX_RATE = new BigDecimal("0.0825");

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderItemRepository orderItemRepository;

    @Autowired
    private CustomizationRepository customizationRepository;

    @Autowired
    private OrderItemCustomizationRepository orderItemCustomizationRepository;

    @PostMapping
    public Order createOrder(@RequestBody CreateOrderRequest request) {
        // 1. Create a new Order
        Order newOrder = new Order();
        newOrder.setOrderDate(LocalDateTime.now());
        newOrder.setStatus(OrderStatus.PENDING);
        newOrder.setPaymentStatus(PaymentStatus.NOT_PAID);
        newOrder.setCustomerId(request.getCustomerId());
        newOrder.setCustomerName(request.getCustomerName());
        newOrder.setCustomerEmail(request.getCustomerEmail());
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

                // Use price from the cart or look up in your MenuItem table
                BigDecimal itemPrice = cartItem.getPriceAtOrderTime();
                orderItem.setItemPrice(itemPrice);

                // Accumulate subtotal
                BigDecimal itemTotal = itemPrice.multiply(BigDecimal.valueOf(cartItem.getQuantity()));
                subtotal = subtotal.add(itemTotal);

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
}
