package com.bistro_template_backend.repositories;

import com.bistro_template_backend.models.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
    List<Payment> findByOrderIdOrderByCreatedAtDesc(Long orderId);

    Payment findByTransactionId(String transactionId);
}