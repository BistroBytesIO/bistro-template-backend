package com.bistro_template_backend.repositories;

import com.bistro_template_backend.models.OrderItemCustomization;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OrderItemCustomizationRepository extends JpaRepository<OrderItemCustomization, Long> {
    List<OrderItemCustomization> findByOrderItemId(Long orderItemId);
}