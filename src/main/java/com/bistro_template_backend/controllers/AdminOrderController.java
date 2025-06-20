package com.bistro_template_backend.controllers;

import com.bistro_template_backend.models.*;
import com.bistro_template_backend.repositories.MenuItemRepository;
import com.bistro_template_backend.repositories.OrderItemCustomizationRepository;
import com.bistro_template_backend.repositories.OrderItemRepository;
import com.bistro_template_backend.repositories.OrderRepository;
import com.bistro_template_backend.services.EmailService;
import com.bistro_template_backend.services.WebSocketOrderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/api/admin/orders")
public class AdminOrderController {

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderItemRepository orderItemRepository;

    @Autowired
    private MenuItemRepository menuItemRepository;

    @Autowired
    private OrderItemCustomizationRepository orderItemCustomizationRepository;

    @Autowired
    private EmailService emailService;

    @Autowired
    WebSocketOrderService webSocketOrderService;

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> getAllOrdersWithDetails() {
        // Get all orders with paid payment status, ordered by most recent first
        List<Order> allOrders = orderRepository.findByPaymentStatusOrderByIdDesc(PaymentStatus.PAID);

        List<Map<String, Object>> orderDetailsList = allOrders.stream().map(order -> {
            Map<String, Object> orderDetails = new HashMap<>();
            orderDetails.put("id", order.getId());
            orderDetails.put("orderDate", order.getOrderDate());
            orderDetails.put("customerName", order.getCustomerName());
            orderDetails.put("customerEmail", order.getCustomerEmail());
            orderDetails.put("customerPhone", order.getCustomerPhone());
            orderDetails.put("status", order.getStatus());
            orderDetails.put("paymentStatus", order.getPaymentStatus());
            orderDetails.put("subTotal", order.getSubTotal());
            orderDetails.put("serviceFee", order.getServiceFee());
            orderDetails.put("totalAmount", order.getTotalAmount());
            orderDetails.put("specialNotes", order.getSpecialNotes());
            orderDetails.put("tax", order.getTax());

            // Fetch Order Items
            List<OrderItem> orderItems = orderItemRepository.findByOrderId(order.getId());
            List<Map<String, Object>> itemsList = orderItems.stream().map(item -> {
                Map<String, Object> itemDetails = new HashMap<>();
                MenuItem menuItem = menuItemRepository.findById(item.getMenuItemId())
                        .orElseThrow(() -> new RuntimeException("Menu item not found"));

                itemDetails.put("name", menuItem.getName());
                itemDetails.put("quantity", item.getQuantity());
                itemDetails.put("price", menuItem.getPrice());

                // Fetch Customizations
                List<OrderItemCustomization> customizations = orderItemCustomizationRepository.findByOrderItemId(item.getId());
                List<Map<String, Object>> customizationsList = customizations.stream().map(customization -> {
                    Map<String, Object> customizationDetails = new HashMap<>();
                    customizationDetails.put("name", customization.getCustomization().getName());
                    customizationDetails.put("price", customization.getCustomization().getPrice());
                    return customizationDetails;
                }).toList();

                itemDetails.put("customizations", customizationsList);
                return itemDetails;
            }).toList();

            orderDetails.put("items", itemsList);
            return orderDetails;
        }).toList();

        return ResponseEntity.ok(orderDetailsList);
    }

    @GetMapping("/pending")
    public ResponseEntity<List<Map<String, Object>>> getPendingOrdersWithDetails() {
        List<Order> pendingOrders = orderRepository.findByStatusAndPaymentStatusOrderByIdDesc(OrderStatus.PENDING, PaymentStatus.PAID);

        List<Map<String, Object>> orderDetailsList = pendingOrders.stream().map(order -> {
            Map<String, Object> orderDetails = new HashMap<>();
            orderDetails.put("id", order.getId());
            orderDetails.put("orderDate", order.getOrderDate());
            orderDetails.put("customerName", order.getCustomerName());
            orderDetails.put("customerEmail", order.getCustomerEmail());
            orderDetails.put("status", order.getStatus());
            orderDetails.put("paymentStatus", order.getPaymentStatus());
            orderDetails.put("subTotal", order.getSubTotal());
            orderDetails.put("serviceFee", order.getServiceFee());
            orderDetails.put("totalAmount", order.getTotalAmount());
            orderDetails.put("specialNotes", order.getSpecialNotes());
            orderDetails.put("tax", order.getTax());

            // Fetch Order Items
            List<OrderItem> orderItems = orderItemRepository.findByOrderId(order.getId());
            List<Map<String, Object>> itemsList = orderItems.stream().map(item -> {
                Map<String, Object> itemDetails = new HashMap<>();
                MenuItem menuItem = menuItemRepository.findById(item.getMenuItemId())
                        .orElseThrow(() -> new RuntimeException("Menu item not found"));

                itemDetails.put("name", menuItem.getName());
                itemDetails.put("quantity", item.getQuantity());
                itemDetails.put("price", menuItem.getPrice());

                // Fetch Customizations
                List<OrderItemCustomization> customizations = orderItemCustomizationRepository.findByOrderItemId(item.getId());
                List<Map<String, Object>> customizationsList = customizations.stream().map(customization -> {
                    Map<String, Object> customizationDetails = new HashMap<>();
                    customizationDetails.put("name", customization.getCustomization().getName());
                    customizationDetails.put("price", customization.getCustomization().getPrice());
                    return customizationDetails;
                }).toList();

                itemDetails.put("customizations", customizationsList);
                return itemDetails;
            }).toList();

            orderDetails.put("items", itemsList);
            return orderDetails;
        }).toList();

        return ResponseEntity.ok(orderDetailsList);
    }

    @PutMapping("/{orderId}/ready")
    public ResponseEntity<?> markOrderAsReady(@PathVariable Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found"));

        // Update the order status to READY
        order.setStatus(OrderStatus.READY_FOR_PICKUP);
        orderRepository.save(order);

        // **NEW: Send WebSocket notification for status update**
        webSocketOrderService.notifyOrderStatusUpdate(order, "READY_FOR_PICKUP");

        // Send email notification to the customer
        String customerEmail = order.getCustomerEmail();
        String subject = "Your Order is Ready for Pickup!";
        String body = "Dear " + order.getCustomerName() + ",\n\n"
                + "Your order #" + order.getId() + " is now ready for pickup.\n\n"
                + "Please visit us to collect your order. Thank you for choosing Your Restaurant Name!\n\n"
                + "Best Regards,\nYour Restaurant Name Team";

        emailService.sendEmail(customerEmail, subject, body);

        return ResponseEntity.ok("Order marked as ready and email sent to customer.");
    }

    @PutMapping("/{orderId}/completed")
    public ResponseEntity<?> markOrderAsCompleted(@PathVariable Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found"));

        // Update the order status to COMPLETED
        order.setStatus(OrderStatus.COMPLETED);
        orderRepository.save(order);

        // **NEW: Send WebSocket notification for status update**
        webSocketOrderService.notifyOrderStatusUpdate(order, "COMPLETED");

        // Send email notification to the customer
        String customerEmail = order.getCustomerEmail();
        String subject = "Your Order has been picked up!";
        String body = "Dear " + order.getCustomerName() + ",\n\n"
                + "Your order #" + order.getId() + " has been picked up.\n\n"
                + "Please come again, thank you for choosing Your Restaurant Name!\n\n"
                + "Best Regards,\nYour Restaurant Name Team";

        emailService.sendEmail(customerEmail, subject, body);

        return ResponseEntity.ok("Order marked as completed and email sent to customer.");
    }

    @GetMapping("/readyForPickup")
    public ResponseEntity<List<Map<String, Object>>> getReadyForPickupOrdersWithDetails() {
        List<Order> pendingOrders = orderRepository.findByStatusAndPaymentStatusOrderByIdDesc(OrderStatus.READY_FOR_PICKUP, PaymentStatus.PAID);

        List<Map<String, Object>> orderDetailsList = pendingOrders.stream().map(order -> {
            Map<String, Object> orderDetails = new HashMap<>();
            orderDetails.put("id", order.getId());
            orderDetails.put("orderDate", order.getOrderDate());
            orderDetails.put("customerName", order.getCustomerName());
            orderDetails.put("customerEmail", order.getCustomerEmail());
            orderDetails.put("status", order.getStatus());
            orderDetails.put("paymentStatus", order.getPaymentStatus());
            orderDetails.put("subTotal", order.getSubTotal());
            orderDetails.put("serviceFee", order.getServiceFee());
            orderDetails.put("totalAmount", order.getTotalAmount());
            orderDetails.put("specialNotes", order.getSpecialNotes());
            orderDetails.put("tax", order.getTax());

            // Fetch Order Items
            List<OrderItem> orderItems = orderItemRepository.findByOrderId(order.getId());
            List<Map<String, Object>> itemsList = orderItems.stream().map(item -> {
                Map<String, Object> itemDetails = new HashMap<>();
                MenuItem menuItem = menuItemRepository.findById(item.getMenuItemId())
                        .orElseThrow(() -> new RuntimeException("Menu item not found"));

                itemDetails.put("name", menuItem.getName());
                itemDetails.put("quantity", item.getQuantity());
                itemDetails.put("price", menuItem.getPrice());

                // Fetch Customizations
                List<OrderItemCustomization> customizations = orderItemCustomizationRepository.findByOrderItemId(item.getId());
                List<Map<String, Object>> customizationsList = customizations.stream().map(customization -> {
                    Map<String, Object> customizationDetails = new HashMap<>();
                    customizationDetails.put("name", customization.getCustomization().getName());
                    customizationDetails.put("price", customization.getCustomization().getPrice());
                    return customizationDetails;
                }).toList();

                itemDetails.put("customizations", customizationsList);
                return itemDetails;
            }).toList();

            orderDetails.put("items", itemsList);
            return orderDetails;
        }).toList();

        return ResponseEntity.ok(orderDetailsList);
    }

    // NEW: Past Orders endpoint for completed orders with pagination and search
    @GetMapping("/past")
    public ResponseEntity<Map<String, Object>> getPastOrdersWithDetails(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String customerName,
            @RequestParam(required = false) Long orderId,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {

        // Create pageable object with sorting by ID descending (most recent first)
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id"));

        Page<Order> completedOrdersPage;

        // If filtering parameters are provided, use custom query
        if (customerName != null || orderId != null || startDate != null || endDate != null) {
            // For simplicity, we'll filter completed orders by customer name and order ID
            // You would implement more complex filtering in the repository layer
            List<Order> allCompleted = orderRepository.findByStatusAndPaymentStatusOrderByIdDesc(OrderStatus.COMPLETED, PaymentStatus.PAID);

            // Basic filtering (you can enhance this with proper SQL queries)
            List<Order> filteredOrders = allCompleted.stream()
                    .filter(order -> customerName == null ||
                            order.getCustomerName().toLowerCase().contains(customerName.toLowerCase()))
                    .filter(order -> orderId == null || order.getId().equals(orderId))
                    .toList();

            // Manual pagination for filtered results
            int start = page * size;
            int end = Math.min(start + size, filteredOrders.size());
            List<Order> pageContent = start >= filteredOrders.size() ?
                    List.of() : filteredOrders.subList(start, end);

            completedOrdersPage = new PageImpl<>(pageContent, pageable, filteredOrders.size());
        } else {
            // Use repository method for pagination without filtering
            completedOrdersPage = orderRepository.findByStatusAndPaymentStatus(
                    OrderStatus.COMPLETED, PaymentStatus.PAID, pageable);
        }

        List<Map<String, Object>> orderDetailsList = completedOrdersPage.getContent().stream().map(order -> {
            Map<String, Object> orderDetails = new HashMap<>();
            orderDetails.put("id", order.getId());
            orderDetails.put("orderDate", order.getOrderDate());
            orderDetails.put("customerName", order.getCustomerName());
            orderDetails.put("customerEmail", order.getCustomerEmail());
            orderDetails.put("customerPhone", order.getCustomerPhone());
            orderDetails.put("status", order.getStatus());
            orderDetails.put("paymentStatus", order.getPaymentStatus());
            orderDetails.put("subTotal", order.getSubTotal());
            orderDetails.put("serviceFee", order.getServiceFee());
            orderDetails.put("totalAmount", order.getTotalAmount());
            orderDetails.put("specialNotes", order.getSpecialNotes());
            orderDetails.put("tax", order.getTax());

            // Fetch Order Items
            List<OrderItem> orderItems = orderItemRepository.findByOrderId(order.getId());
            List<Map<String, Object>> itemsList = orderItems.stream().map(item -> {
                Map<String, Object> itemDetails = new HashMap<>();
                MenuItem menuItem = menuItemRepository.findById(item.getMenuItemId())
                        .orElseThrow(() -> new RuntimeException("Menu item not found"));

                itemDetails.put("name", menuItem.getName());
                itemDetails.put("quantity", item.getQuantity());
                itemDetails.put("price", menuItem.getPrice());

                // Fetch Customizations
                List<OrderItemCustomization> customizations = orderItemCustomizationRepository.findByOrderItemId(item.getId());
                List<Map<String, Object>> customizationsList = customizations.stream().map(customization -> {
                    Map<String, Object> customizationDetails = new HashMap<>();
                    customizationDetails.put("name", customization.getCustomization().getName());
                    customizationDetails.put("price", customization.getCustomization().getPrice());
                    return customizationDetails;
                }).toList();

                itemDetails.put("customizations", customizationsList);
                return itemDetails;
            }).toList();

            orderDetails.put("items", itemsList);
            return orderDetails;
        }).toList();

        // Return paginated response
        Map<String, Object> response = new HashMap<>();
        response.put("orders", orderDetailsList);
        response.put("currentPage", completedOrdersPage.getNumber());
        response.put("totalPages", completedOrdersPage.getTotalPages());
        response.put("totalElements", completedOrdersPage.getTotalElements());
        response.put("pageSize", completedOrdersPage.getSize());
        response.put("hasNext", completedOrdersPage.hasNext());
        response.put("hasPrevious", completedOrdersPage.hasPrevious());

        return ResponseEntity.ok(response);
    }
}