package com.bistro_template_backend.controllers;

import com.bistro_template_backend.models.MenuItem;
import com.bistro_template_backend.repositories.OrderRepository;
import com.bistro_template_backend.repositories.MenuItemRepository;
import com.bistro_template_backend.repositories.OrderItemRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/api/admin/stats")
public class AdminStatsController {

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private MenuItemRepository menuItemRepository;

    @Autowired
    private OrderItemRepository orderItemRepository;

    @GetMapping("/dashboard")
    public ResponseEntity<Map<String, Object>> getDashboardStats() {
        Map<String, Object> stats = new HashMap<>();

        // Get today's date for filtering
        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();

        // Count today's orders
        Long todayOrdersCount = orderRepository.countByOrderDateAfter(startOfDay);
        stats.put("todayOrders", todayOrdersCount);

        // Calculate total revenue (all time or you could filter by month/year)
        BigDecimal totalRevenue = orderRepository.sumTotalAmount();
        stats.put("totalRevenue", totalRevenue);

        // Get total customers (unique by email)
        Long totalCustomers = orderRepository.countDistinctCustomerEmail();
        stats.put("totalCustomers", totalCustomers);

        // Find popular items (top 3 ordered items)
        List<Map<String, Object>> popularItems = orderItemRepository.findTopOrderedItems(3);
        stats.put("popularItems", popularItems);

        return ResponseEntity.ok(stats);
    }

    @GetMapping("/revenue/monthly")
    public ResponseEntity<List<Map<String, Object>>> getMonthlyRevenue() {
        // Return revenue grouped by month for the last year
        // This could be used to create charts
        List<Map<String, Object>> monthlyStats = orderRepository.getMonthlyRevenue();
        return ResponseEntity.ok(monthlyStats);
    }

    @GetMapping("/inventory/low-stock")
    public ResponseEntity<List<MenuItem>> getLowStockItems() {
        // Find items with stock below threshold (e.g., 10)
        List<MenuItem> lowStockItems = menuItemRepository.findByStockQuantityLessThan(10);
        return ResponseEntity.ok(lowStockItems);
    }

    @GetMapping("/performance")
    public ResponseEntity<Map<String, Object>> getPerformanceStats() {
        Map<String, Object> stats = new HashMap<>();

        // Average order value
        BigDecimal avgOrderValue = orderRepository.getAverageOrderValue();
        stats.put("averageOrderValue", avgOrderValue);

        // Orders by status counts
        Map<String, Long> ordersByStatus = orderRepository.countOrdersByStatus();
        stats.put("ordersByStatus", ordersByStatus);

        // Category performance
        List<Map<String, Object>> categoryPerformance = menuItemRepository.getCategoryPerformance();
        stats.put("categoryPerformance", categoryPerformance);

        return ResponseEntity.ok(stats);
    }
}