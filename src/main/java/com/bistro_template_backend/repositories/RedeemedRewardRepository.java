package com.bistro_template_backend.repositories;

import com.bistro_template_backend.models.RedeemedReward;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RedeemedRewardRepository extends JpaRepository<RedeemedReward, Long> {

    List<RedeemedReward> findByCustomerAccountIdOrderByDateRedeemedDesc(Long customerAccountId);

    @Query("SELECT r FROM RedeemedReward r WHERE r.customerAccountId = :customerAccountId AND r.quantityAvailable > 0 ORDER BY r.dateRedeemed DESC")
    List<RedeemedReward> findAvailableByCustomerAccountId(@Param("customerAccountId") Long customerAccountId);
}