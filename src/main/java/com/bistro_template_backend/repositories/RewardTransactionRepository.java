// File: src/main/java/com/bistro_template_backend/repositories/RewardTransactionRepository.java
package com.bistro_template_backend.repositories;

import com.bistro_template_backend.models.RewardTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface RewardTransactionRepository extends JpaRepository<RewardTransaction, Long> {

    // Updated method with proper limit handling
    @Query("SELECT rt FROM RewardTransaction rt WHERE rt.customerAccountId = :customerAccountId ORDER BY rt.createdAt DESC")
    List<RewardTransaction> findByCustomerAccountIdOrderByCreatedAtDesc(@Param("customerAccountId") Long customerAccountId);

    // Method for getting top 10 transactions (Spring Data JPA automatically handles this)
    List<RewardTransaction> findTop10ByCustomerAccountIdOrderByCreatedAtDesc(Long customerAccountId);

    // Default method to handle the limit parameter that the service is expecting
    default List<RewardTransaction> findByCustomerAccountIdOrderByCreatedAtDesc(Long customerAccountId, Integer limit) {
        if (limit == null || limit > 10) {
            return findByCustomerAccountIdOrderByCreatedAtDesc(customerAccountId);
        }
        // For PostgreSQL, we'll use the Top10 method as a fallback
        return findTop10ByCustomerAccountIdOrderByCreatedAtDesc(customerAccountId);
    }

    // Existing methods
    RewardTransaction findByOrderIdAndTransactionType(Long orderId, String transactionType);

    RewardTransaction findByReferenceId(String referenceId);

    // Additional query method for filtering by transaction type
    @Query("SELECT rt FROM RewardTransaction rt WHERE rt.customerAccountId = :customerAccountId AND rt.transactionType = :type ORDER BY rt.createdAt DESC")
    List<RewardTransaction> findByCustomerAccountIdAndTransactionTypeOrderByCreatedAtDesc(
            @Param("customerAccountId") Long customerAccountId,
            @Param("type") String transactionType);

    // Method to get transactions within a date range
    @Query("SELECT rt FROM RewardTransaction rt WHERE rt.customerAccountId = :customerAccountId AND rt.createdAt BETWEEN :startDate AND :endDate ORDER BY rt.createdAt DESC")
    List<RewardTransaction> findByCustomerAccountIdAndCreatedAtBetweenOrderByCreatedAtDesc(
            @Param("customerAccountId") Long customerAccountId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);

    // Method to get total points earned for analytics
    @Query("SELECT COALESCE(SUM(rt.pointsAmount), 0) FROM RewardTransaction rt WHERE rt.customerAccountId = :customerAccountId AND rt.transactionType IN ('EARNED', 'BONUS')")
    Integer getTotalPointsEarnedByCustomer(@Param("customerAccountId") Long customerAccountId);

    // Method to get total points redeemed for analytics
    @Query("SELECT COALESCE(SUM(rt.pointsAmount), 0) FROM RewardTransaction rt WHERE rt.customerAccountId = :customerAccountId AND rt.transactionType = 'REDEEMED'")
    Integer getTotalPointsRedeemedByCustomer(@Param("customerAccountId") Long customerAccountId);
}