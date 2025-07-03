package com.bistro_template_backend.repositories;

import com.bistro_template_backend.models.MenuItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Map;

public interface MenuItemRepository extends JpaRepository<MenuItem, Long> {
    List<MenuItem> findByComplexItemTrue();

    List<MenuItem> findByCategoryId(Long categoryId);

    List<MenuItem> findByIsFeaturedTrue();

    // Voice AI integration methods
    List<MenuItem> findByIsAvailable(Boolean isAvailable);
    List<MenuItem> findByCategoryAndIsAvailable(String category, Boolean isAvailable);
    List<MenuItem> findByIsFeaturedAndIsAvailable(Boolean isFeatured, Boolean isAvailable);
    List<MenuItem> findByNameContainingIgnoreCaseAndIsAvailable(String name, Boolean isAvailable);

    // Existing methods...

    // For low stock alerts
    List<MenuItem> findByStockQuantityLessThan(Integer threshold);

    // For category performance
    @Query(value =
            "SELECT c.name as name, " +
                    "SUM(oi.item_price * oi.quantity) as revenue, " +
                    "100.0 * SUM(oi.item_price * oi.quantity) / " +
                    "(SELECT SUM(o2.item_price * o2.quantity) FROM order_items o2) as percentage " +
                    "FROM menu_items mi " +
                    "JOIN categories c ON mi.category_id = c.id " +
                    "JOIN order_items oi ON oi.menu_item_id = mi.id " +
                    "GROUP BY c.name " +
                    "ORDER BY revenue DESC",
            nativeQuery = true)
    List<Map<String, Object>> getCategoryPerformance();

    List<MenuItem> findByIsRewardItemTrueOrderByPointsToRedeemAsc();
}
