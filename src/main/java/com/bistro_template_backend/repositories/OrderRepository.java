package com.bistro_template_backend.repositories;

import com.bistro_template_backend.models.Order;
import com.bistro_template_backend.models.OrderStatus;
import com.bistro_template_backend.models.PaymentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {
    List<Order> findByStatusAndPaymentStatusOrderByIdDesc(OrderStatus status, PaymentStatus paymentStatus);

    // NEW: Find all paid orders (regardless of status) ordered by ID descending
    List<Order> findByPaymentStatusOrderByIdDesc(PaymentStatus paymentStatus);

    // NEW: Paginated version for past orders
    Page<Order> findByStatusAndPaymentStatus(OrderStatus status, PaymentStatus paymentStatus, Pageable pageable);

    // NEW: Find all orders ordered by ID descending
    List<Order> findAllByOrderByIdDesc();

    // Count orders after a given datetime
    @Query("SELECT COUNT(o) FROM Order o WHERE o.orderDate >= :date")
    Long countByOrderDateAfter(@Param("date") LocalDateTime date);

    // Sum total amount of all orders (or could be filtered)
    @Query("SELECT SUM(o.totalAmount) FROM Order o")
    BigDecimal sumTotalAmount();

    // Count distinct customers
    @Query("SELECT COUNT(DISTINCT o.customerEmail) FROM Order o")
    Long countDistinctCustomerEmail();

    // For monthly revenue chart
    @Query(value =
            "SELECT TO_CHAR(o.order_date, 'YYYY-MM') as month, " +
                    "SUM(o.total_amount) as revenue " +
                    "FROM orders o " +
                    "WHERE o.order_date >= CURRENT_DATE - INTERVAL '12 months' " +
                    "GROUP BY TO_CHAR(o.order_date, 'YYYY-MM') " +
                    "ORDER BY month ASC",
            nativeQuery = true)
    List<Map<String, Object>> getMonthlyRevenue();

    // For average order value
    @Query("SELECT AVG(o.totalAmount) FROM Order o WHERE o.status != 'CANCELED'")
    BigDecimal getAverageOrderValue();

    // For orders by status count
    @Query("SELECT o.status as status, COUNT(o) as count FROM Order o GROUP BY o.status")
    List<Map<String, Object>> getOrderCountsByStatus();

    // Helper method to convert the above result to a Map<String, Long>
    default Map<String, Long> countOrdersByStatus() {
        List<Map<String, Object>> results = getOrderCountsByStatus();
        Map<String, Long> countsByStatus = new HashMap<>();

        for (Map<String, Object> result : results) {
            String status = result.get("status").toString();
            Long count = ((Number) result.get("count")).longValue();
            countsByStatus.put(status, count);
        }

        return countsByStatus;
    }
}