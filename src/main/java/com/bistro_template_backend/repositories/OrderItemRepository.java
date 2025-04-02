package com.bistro_template_backend.repositories;

import com.bistro_template_backend.models.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Map;

public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {
    List<OrderItem> findByOrderId(Long orderId);

    // Find top ordered items
    @Query(value = "SELECT mi.name as name, COUNT(oi.id) as count " +
            "FROM order_items oi " +
            "JOIN menu_items mi ON oi.menu_item_id = mi.id " +
            "GROUP BY mi.name " +
            "ORDER BY COUNT(oi.id) DESC " +
            "LIMIT :limit", nativeQuery = true)
    List<Map<String, Object>> findTopOrderedItems(@Param("limit") int limit);}