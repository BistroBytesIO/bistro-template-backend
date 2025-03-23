package com.bistro_template_backend.services;

import com.bistro_template_backend.models.MenuItem;
import com.bistro_template_backend.models.Order;
import com.bistro_template_backend.models.OrderItem;
import com.bistro_template_backend.repositories.MenuItemRepository;
import com.bistro_template_backend.repositories.OrderItemRepository;
import com.bistro_template_backend.repositories.OrderRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Service
public class OrderService {

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderItemRepository orderItemRepository;

    @Autowired
    private MenuItemRepository menuItemRepository;

//    @Autowired
//    private CouponRepository couponRepository; // optional, if you have coupons

    // 1. Called after user has created an order with items
    //    This calculates the costs, applies coupon, checks inventory, etc.
    public Order calculateOrderTotals(Long orderId, String couponCode) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found"));

        List<OrderItem> items = orderItemRepository.findByOrderId(orderId);

        // 2. Calculate subTotal
        BigDecimal subTotal = BigDecimal.ZERO;
        for (OrderItem item : items) {
            subTotal = subTotal.add(item.getItemPrice()
                    .multiply(BigDecimal.valueOf(item.getQuantity())));
        }

        // 3. Calculate tax (example 8%)
        BigDecimal tax = subTotal.multiply(new BigDecimal("0.0825"));

        // 4. Shipping or convenience fee
        BigDecimal serviceFee = new BigDecimal("5.00");

        // 5. Apply coupon/discount if applicable
//        BigDecimal discount = BigDecimal.ZERO;
//        if (couponCode != null && !couponCode.isEmpty()) {
//            // Example: find coupon in DB
//            Optional<Coupon> couponOpt = couponRepository.findByCode(couponCode);
//            if (couponOpt.isPresent()) {
//                Coupon coupon = couponOpt.get();
//                // E.g. if coupon is 10% off
//                discount = subTotal.multiply(coupon.getDiscountPercentage());
//            }
//        }

        // 6. Compute total = subTotal + tax + shipping - discount
        BigDecimal total = subTotal.add(tax)
                .add(serviceFee);
//                .subtract(discount);

        // 7. Update order fields
        order.setSubTotal(subTotal);
        order.setTax(tax);
        order.setServiceFee(serviceFee);
//        order.setDiscount(BigDecimal.valueOf(0));
        order.setTotalAmount(total);

        // 8. (Optional) Validate inventory before finalizing
        validateInventory(items);

        // 9. Save changes
        return orderRepository.save(order);
    }

    /**
     * Example method to reduce stock after the order is fully paid,
     * or just to check if enough stock is available.
     */
    private void validateInventory(List<OrderItem> items) {
        for (OrderItem oi : items) {
            MenuItem menuItem = menuItemRepository.findById(oi.getMenuItemId())
                    .orElseThrow(() -> new RuntimeException("Menu item not found"));
            // Check if enough stock
            if (menuItem.getStockQuantity() != null) {
                if (menuItem.getStockQuantity() < oi.getQuantity()) {
                    throw new RuntimeException("Not enough stock for item: " + menuItem.getName());
                }
            }
        }
    }

    // 2.2. After payment success, reduce actual stock
    public void reduceStockAfterPayment(Order order) {
        List<OrderItem> items = orderItemRepository.findByOrderId(order.getId());
        for (OrderItem oi : items) {
            MenuItem menuItem = menuItemRepository.findById(oi.getMenuItemId())
                    .orElseThrow(() -> new RuntimeException("Menu item not found"));
            if (menuItem.getStockQuantity() != null) {
                int newQty = menuItem.getStockQuantity() - oi.getQuantity();
                if (newQty < 0) {
                    throw new RuntimeException("Stock inconsistency for item: " + menuItem.getName());
                }
                menuItem.setStockQuantity(newQty);
                menuItemRepository.save(menuItem);
            }
        }
    }
}