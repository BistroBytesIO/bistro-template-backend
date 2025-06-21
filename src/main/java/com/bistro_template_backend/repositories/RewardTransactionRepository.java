package com.bistro_template_backend.repositories;

import com.bistro_template_backend.models.RewardTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface RewardTransactionRepository extends JpaRepository<RewardTransaction, Long> {

    @Query("SELECT rt FROM RewardTransaction rt WHERE rt.customerAccountId = :customerAccountId ORDER BY rt.createdAt DESC")
    List<RewardTransaction> findByCustomerAccountIdOrderByCreatedAtDesc(@Param("customerAccountId") Long customerAccountId, Integer limit);

    RewardTransaction findByOrderIdAndTransactionType(Long orderId, String transactionType);

    RewardTransaction findByReferenceId(String referenceId);
}
