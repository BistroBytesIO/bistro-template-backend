package com.bistro_template_backend.services;

import com.bistro_template_backend.config.CognitoConfig;
import com.bistro_template_backend.models.CustomerAccount;
import com.bistro_template_backend.repositories.CustomerAccountRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;

@Slf4j
@Service
public class CognitoService {

    @Autowired
    private CognitoConfig cognitoConfig;

    @Autowired
    private CustomerAccountRepository customerAccountRepository;

    /**
     * Gets current authenticated user's Cognito ID
     */
    public String getCurrentCognitoUserId() {
        try {
            Jwt jwt = (Jwt) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
            return jwt.getSubject();
        } catch (Exception e) {
            log.warn("Could not get current Cognito user ID: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Gets current authenticated user's email
     */
    public String getCurrentUserEmail() {
        try {
            Jwt jwt = (Jwt) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
            return jwt.getClaimAsString("email");
        } catch (Exception e) {
            log.warn("Could not get current user email: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Gets or creates customer account from JWT token
     */
    @Transactional
    public CustomerAccount getCurrentCustomerAccount() {
        try {
            Jwt jwt = (Jwt) SecurityContextHolder.getContext().getAuthentication().getPrincipal();

            String cognitoUserId = jwt.getSubject();
            String email = jwt.getClaimAsString("email");

            // Try to find existing account
            CustomerAccount account = customerAccountRepository.findByCognitoUserId(cognitoUserId);

            if (account == null) {
                // Create new account
                account = createAccountFromJwt(jwt);
                log.info("Created new customer account for user: {}", email);
            } else {
                // Update last login
                account.setLastLogin(LocalDateTime.now());
                account = customerAccountRepository.save(account);
            }

            return account;

        } catch (Exception e) {
            log.error("Error getting current customer account: {}", e.getMessage(), e);
            throw new RuntimeException("Could not get customer account", e);
        }
    }

    /**
     * Creates customer account from JWT claims
     */
    private CustomerAccount createAccountFromJwt(Jwt jwt) {
        CustomerAccount account = new CustomerAccount();
        account.setCognitoUserId(jwt.getSubject());
        account.setEmail(jwt.getClaimAsString("email"));
        account.setFirstName(jwt.getClaimAsString("given_name"));
        account.setLastName(jwt.getClaimAsString("family_name"));
        account.setPhoneNumber(jwt.getClaimAsString("phone_number"));

        // Parse birthday if provided
        String birthdayStr = jwt.getClaimAsString("birthdate");
        if (birthdayStr != null && !birthdayStr.trim().isEmpty()) {
            try {
                account.setBirthday(LocalDate.parse(birthdayStr));
            } catch (DateTimeParseException e) {
                log.warn("Could not parse birthday: {}", birthdayStr);
            }
        }

        account.setLastLogin(LocalDateTime.now());
        account = customerAccountRepository.save(account);

        // Award signup bonus (create the RewardsService first)
        // rewardsService.awardSignupBonus(account.getId());

        return account;
    }
}