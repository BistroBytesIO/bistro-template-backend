package com.bistro_template_backend.services;

import com.bistro_template_backend.dto.CreateOrderRequest;
import com.bistro_template_backend.dto.CartItemDTO;
import com.bistro_template_backend.models.*;
import com.bistro_template_backend.repositories.*;
import com.bistro_template_backend.services.VoiceSessionManager.VoiceOrder;
import com.bistro_template_backend.services.VoiceSessionManager.VoiceOrderItem;
import com.bistro_template_backend.services.VoiceSessionManager.VoiceSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class VoiceOrderService {

    private final VoiceSessionManager voiceSessionManager;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final MenuItemRepository menuItemRepository;
    private final CustomizationRepository customizationRepository;
    private final OrderItemCustomizationRepository orderItemCustomizationRepository;
    private final PaymentRepository paymentRepository;
    private final CustomerRepository customerRepository;
    @Lazy private final WebSocketOrderService webSocketOrderService;
    @Lazy private final EmailService emailService;

    private static final BigDecimal TAX_RATE = new BigDecimal("0.0825");

    /**
     * Create a regular order from a voice session
     */
    @Transactional
    public Long createOrderFromVoiceSession(String sessionId, Map<String, String> additionalInfo) {
        try {
            log.info("Creating order from voice session: {}", sessionId);

            VoiceSession session = voiceSessionManager.getSession(sessionId);
            VoiceOrder voiceOrder = session.getCurrentOrder();

            // Validate voice order has items
            if (voiceOrder.getItems().isEmpty()) {
                throw new IllegalArgumentException("Cannot create order: no items in voice order");
            }

            // Create new Order entity
            Order order = new Order();
            order.setOrderDate(LocalDateTime.now());
            order.setStatus(OrderStatus.PENDING);
            order.setPaymentStatus(PaymentStatus.NOT_PAID);
            
            // Set customer information
            setCustomerInformation(order, session, additionalInfo);
            
            // Set special instructions
            if (voiceOrder.getSpecialInstructions() != null) {
                order.setSpecialNotes(voiceOrder.getSpecialInstructions());
            }

            // Save order to get ID
            order = orderRepository.save(order);
            log.debug("Created order with ID: {}", order.getId());

            // Process each voice order item
            BigDecimal subtotal = processVoiceOrderItems(order, voiceOrder.getItems());

            // Calculate totals
            calculateOrderTotals(order, subtotal);

            // Save final order with totals
            order = orderRepository.save(order);

            // Send WebSocket notification
            webSocketOrderService.notifyNewOrder(order);

            log.info("Successfully created order {} from voice session {}", order.getId(), sessionId);
            return order.getId();

        } catch (Exception e) {
            log.error("Error creating order from voice session: {}", sessionId, e);
            throw new RuntimeException("Failed to create order from voice session: " + e.getMessage(), e);
        }
    }

    /**
     * Convert voice order to CreateOrderRequest for compatibility
     */
    public CreateOrderRequest convertVoiceOrderToRequest(String sessionId) {
        try {
            VoiceSession session = voiceSessionManager.getSession(sessionId);
            VoiceOrder voiceOrder = session.getCurrentOrder();

            CreateOrderRequest request = new CreateOrderRequest();
            request.setCustomerId(Long.valueOf(session.getCustomerId()));
            request.setCustomerEmail(session.getCustomerEmail());
            request.setSpecialNotes(voiceOrder.getSpecialInstructions());

            // Convert voice order items to cart items
            List<CreateOrderRequest.CartItemDTO> cartItems = new ArrayList<>();
            for (VoiceOrderItem voiceItem : voiceOrder.getItems()) {
                CreateOrderRequest.CartItemDTO cartItem = new CreateOrderRequest.CartItemDTO();
                cartItem.setMenuItemId(voiceItem.getMenuItemId());
                cartItem.setQuantity(voiceItem.getQuantity());
                
                // Set price from voice order or lookup from database
                if (voiceItem.getPrice() != null) {
                    cartItem.setPriceAtOrderTime(BigDecimal.valueOf(voiceItem.getPrice()));
                } else {
                    MenuItem menuItem = menuItemRepository.findById(voiceItem.getMenuItemId())
                            .orElseThrow(() -> new RuntimeException("Menu item not found: " + voiceItem.getMenuItemId()));
                    cartItem.setPriceAtOrderTime(menuItem.getPrice());
                }

                // Handle customizations (simplified for now)
                List<CreateOrderRequest.CartItemDTO.CustomizationDTO> customizations = new ArrayList<>();
                for (String customizationName : voiceItem.getCustomizations()) {
                    Optional<Customization> customization = customizationRepository.findByName(customizationName);
                    if (customization.isPresent()) {
                        CreateOrderRequest.CartItemDTO.CustomizationDTO dto = new CreateOrderRequest.CartItemDTO.CustomizationDTO();
                        dto.setId(customization.get().getId());
                        // Note: CustomizationDTO only stores ID, name can be looked up from database later
                        customizations.add(dto);
                    }
                }
                cartItem.setCustomizations(customizations);

                cartItems.add(cartItem);
            }

            request.setItems(cartItems);
            return request;

        } catch (Exception e) {
            log.error("Error converting voice order to request for session: {}", sessionId, e);
            throw new RuntimeException("Failed to convert voice order: " + e.getMessage(), e);
        }
    }

    /**
     * Validate voice order before conversion
     */
    public ValidationResult validateVoiceOrder(String sessionId) {
        try {
            VoiceSession session = voiceSessionManager.getSession(sessionId);
            VoiceOrder voiceOrder = session.getCurrentOrder();

            ValidationResult result = new ValidationResult();
            result.setValid(true);
            result.setErrors(new ArrayList<>());

            // Check if order has items
            if (voiceOrder.getItems().isEmpty()) {
                result.setValid(false);
                result.getErrors().add("Order must contain at least one item");
            }

            // Validate each item
            for (VoiceOrderItem item : voiceOrder.getItems()) {
                validateVoiceOrderItem(item, result);
            }

            // Check total amount meets minimum
            if (voiceOrder.getTotal() != null && voiceOrder.getTotal() < 0.50) {
                result.setValid(false);
                result.getErrors().add("Order total must be at least $0.50");
            }

            // Validate customer information
            if (session.getCustomerEmail() == null || session.getCustomerEmail().trim().isEmpty()) {
                result.getErrors().add("Customer email is required");
            }

            return result;

        } catch (Exception e) {
            log.error("Error validating voice order for session: {}", sessionId, e);
            ValidationResult result = new ValidationResult();
            result.setValid(false);
            result.setErrors(List.of("Validation error: " + e.getMessage()));
            return result;
        }
    }

    /**
     * Update voice order item quantities and pricing
     */
    public void updateVoiceOrderPricing(String sessionId) {
        try {
            VoiceSession session = voiceSessionManager.getSession(sessionId);
            VoiceOrder voiceOrder = session.getCurrentOrder();

            for (VoiceOrderItem item : voiceOrder.getItems()) {
                // Update price from current menu if not set
                if (item.getPrice() == null && item.getMenuItemId() != null) {
                    MenuItem menuItem = menuItemRepository.findById(item.getMenuItemId())
                            .orElse(null);
                    if (menuItem != null) {
                        item.setPrice(menuItem.getPrice().doubleValue());
                    }
                }
            }

            // Recalculate totals
            double subtotal = voiceOrder.getItems().stream()
                    .mapToDouble(item -> item.getPrice() * item.getQuantity())
                    .sum();

            double tax = subtotal * TAX_RATE.doubleValue();
            double serviceFee = subtotal * 0.029 + 0.30;
            double total = subtotal + tax + serviceFee;

            voiceOrder.setSubtotal(subtotal);
            voiceOrder.setTax(tax);
            voiceOrder.setTotal(total);

            // Update session
            voiceSessionManager.updateOrderContext(sessionId, voiceOrder);

            log.debug("Updated pricing for voice order in session: {}", sessionId);

        } catch (Exception e) {
            log.error("Error updating voice order pricing for session: {}", sessionId, e);
        }
    }

    /**
     * Add item to voice order
     */
    public void addItemToVoiceOrder(String sessionId, Long menuItemId, Integer quantity, 
                                   List<String> customizations, String notes) {
        try {
            VoiceSession session = voiceSessionManager.getSession(sessionId);
            VoiceOrder voiceOrder = session.getCurrentOrder();

            // Get menu item details
            MenuItem menuItem = menuItemRepository.findById(menuItemId)
                    .orElseThrow(() -> new RuntimeException("Menu item not found: " + menuItemId));

            // Check availability
            if (!menuItem.getIsAvailable()) {
                throw new RuntimeException("Menu item is not available: " + menuItem.getName());
            }

            // Check stock
            if (menuItem.getStockQuantity() != null && menuItem.getStockQuantity() < quantity) {
                throw new RuntimeException("Insufficient stock for item: " + menuItem.getName());
            }

            // Create voice order item
            VoiceOrderItem voiceItem = new VoiceOrderItem();
            voiceItem.setMenuItemId(menuItemId);
            voiceItem.setName(menuItem.getName());
            voiceItem.setQuantity(quantity);
            voiceItem.setPrice(menuItem.getPrice().doubleValue());
            voiceItem.setCustomizations(customizations != null ? customizations : new ArrayList<>());
            voiceItem.setNotes(notes);

            // Add to order
            voiceOrder.getItems().add(voiceItem);

            // Update session
            voiceSessionManager.updateOrderContext(sessionId, voiceOrder);

            log.info("Added item to voice order - Session: {}, Item: {} x{}", 
                    sessionId, menuItem.getName(), quantity);

        } catch (Exception e) {
            log.error("Error adding item to voice order - Session: {}, Item: {}", sessionId, menuItemId, e);
            throw new RuntimeException("Failed to add item to order: " + e.getMessage(), e);
        }
    }

    /**
     * Remove item from voice order
     */
    public void removeItemFromVoiceOrder(String sessionId, String itemName) {
        try {
            VoiceSession session = voiceSessionManager.getSession(sessionId);
            VoiceOrder voiceOrder = session.getCurrentOrder();

            boolean removed = voiceOrder.getItems().removeIf(
                    item -> item.getName().equalsIgnoreCase(itemName)
            );

            if (removed) {
                voiceSessionManager.updateOrderContext(sessionId, voiceOrder);
                log.info("Removed item from voice order - Session: {}, Item: {}", sessionId, itemName);
            } else {
                log.warn("Item not found in voice order - Session: {}, Item: {}", sessionId, itemName);
            }

        } catch (Exception e) {
            log.error("Error removing item from voice order - Session: {}, Item: {}", sessionId, itemName, e);
            throw new RuntimeException("Failed to remove item from order: " + e.getMessage(), e);
        }
    }

    // Helper methods

    private void setCustomerInformation(Order order, VoiceSession session, Map<String, String> additionalInfo) {
        order.setCustomerId(Long.valueOf(session.getCustomerId()));
        order.setCustomerEmail(session.getCustomerEmail());
        
        // Use additional info if provided, otherwise try to get from existing customer record
        if (additionalInfo.containsKey("customerName")) {
            order.setCustomerName(additionalInfo.get("customerName"));
        }
        
        if (additionalInfo.containsKey("customerPhone")) {
            order.setCustomerPhone(additionalInfo.get("customerPhone"));
        }

        // Try to get customer info from database if not provided
        if (order.getCustomerName() == null && session.getCustomerId() != null) {
            customerRepository.findById(Long.valueOf(session.getCustomerId()))
                    .ifPresent(customer -> {
                        if (order.getCustomerName() == null) {
                            order.setCustomerName(customer.getFullName());
                        }
                        if (order.getCustomerPhone() == null) {
                            order.setCustomerPhone(customer.getPhone());
                        }
                        if (order.getCustomerEmail() == null) {
                            order.setCustomerEmail(customer.getEmail());
                        }
                    });
        }
    }

    private BigDecimal processVoiceOrderItems(Order order, List<VoiceOrderItem> voiceItems) {
        BigDecimal subtotal = BigDecimal.ZERO;

        for (VoiceOrderItem voiceItem : voiceItems) {
            // Get menu item from database
            MenuItem menuItem = menuItemRepository.findById(voiceItem.getMenuItemId())
                    .orElseThrow(() -> new RuntimeException("Menu item not found: " + voiceItem.getMenuItemId()));

            // Create order item
            OrderItem orderItem = new OrderItem();
            orderItem.setOrderId(order.getId());
            orderItem.setMenuItemId(voiceItem.getMenuItemId());
            orderItem.setQuantity(voiceItem.getQuantity());
            orderItem.setItemPrice(menuItem.getPrice());

            // Calculate item total
            BigDecimal itemTotal = menuItem.getPrice().multiply(BigDecimal.valueOf(voiceItem.getQuantity()));
            subtotal = subtotal.add(itemTotal);

            // Save order item
            orderItem = orderItemRepository.save(orderItem);

            // Process customizations
            processItemCustomizations(orderItem, voiceItem.getCustomizations());
        }

        return subtotal;
    }

    private void processItemCustomizations(OrderItem orderItem, List<String> customizationNames) {
        for (String customizationName : customizationNames) {
            Optional<Customization> customizationOpt = customizationRepository.findByName(customizationName);
            if (customizationOpt.isPresent()) {
                OrderItemCustomization orderItemCustomization = new OrderItemCustomization();
                orderItemCustomization.setOrderItem(orderItem);
                orderItemCustomization.setCustomization(customizationOpt.get());
                orderItemCustomizationRepository.save(orderItemCustomization);
            } else {
                log.warn("Customization not found: {}", customizationName);
            }
        }
    }

    private void calculateOrderTotals(Order order, BigDecimal subtotal) {
        // Calculate tax
        BigDecimal tax = subtotal.multiply(TAX_RATE);

        // Calculate service fee
        BigDecimal serviceFee = subtotal.multiply(BigDecimal.valueOf(0.029)).add(BigDecimal.valueOf(0.30));

        // Calculate total
        BigDecimal total = subtotal.add(tax).add(serviceFee);

        // Set totals
        order.setSubTotal(subtotal);
        order.setTax(tax);
        order.setServiceFee(serviceFee);
        order.setTotalAmount(total);
    }

    private void validateVoiceOrderItem(VoiceOrderItem item, ValidationResult result) {
        if (item.getMenuItemId() == null) {
            result.getErrors().add("Menu item ID is required for item: " + item.getName());
            result.setValid(false);
        } else {
            // Check if menu item exists and is available
            Optional<MenuItem> menuItemOpt = menuItemRepository.findById(item.getMenuItemId());
            if (menuItemOpt.isEmpty()) {
                result.getErrors().add("Menu item not found: " + item.getName());
                result.setValid(false);
            } else {
                MenuItem menuItem = menuItemOpt.get();
                if (!menuItem.getIsAvailable()) {
                    result.getErrors().add("Menu item is not available: " + item.getName());
                    result.setValid(false);
                }
                
                if (menuItem.getStockQuantity() != null && menuItem.getStockQuantity() < item.getQuantity()) {
                    result.getErrors().add("Insufficient stock for item: " + item.getName());
                    result.setValid(false);
                }
            }
        }

        if (item.getQuantity() == null || item.getQuantity() <= 0) {
            result.getErrors().add("Valid quantity is required for item: " + item.getName());
            result.setValid(false);
        }
    }

    // Data classes
    @lombok.Data
    public static class ValidationResult {
        private boolean valid;
        private List<String> errors;
    }
}