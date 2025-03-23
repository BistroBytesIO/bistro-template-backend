package com.bistro_template_backend.repositories;

import com.bistro_template_backend.models.Order;
import com.bistro_template_backend.models.OrderStatus;
import com.bistro_template_backend.models.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {
    List<Order> findByStatusAndPaymentStatusOrderById(OrderStatus status, PaymentStatus paymentStatus);

}