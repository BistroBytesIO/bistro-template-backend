package com.bistro_template_backend.repositories;

import com.bistro_template_backend.models.CustomerAccount;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomerAccountRepository extends JpaRepository<CustomerAccount, Long> {
    CustomerAccount findByCognitoUserId(String cognitoUserId);
    CustomerAccount findByEmail(String email);
}
