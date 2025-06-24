// File: src/main/java/com/bistro_template_backend/services/CognitoService.java
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
public class CognitoService {

    @Autowired
    private CognitoConfig cognitoConfig;

    @Autowired
    private CustomerAccountRepository customerAccountRepository;

    @Autowired(required = false)  // Optional injection to avoid circular dependency
    private RewardsService rewardsService;

    // Cache to prevent duplicate account creation during concurrent requests
    private final ConcurrentHashMap<String, Object> accountCreationLocks = new ConcurrentHashMap<>();

    /**
     * Gets current authenticated user's Cognito ID
     */
    public String getCurrentCognitoUserId() {
        try {
            Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
            if (principal instanceof Jwt) {
                Jwt jwt = (Jwt) principal;
                return jwt.getClaimAsString("sub");
            } else {
                // Principal is not a JWT (e.g., admin authentication), so no Cognito user ID
                return null;
            }
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
            Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
            if (principal instanceof Jwt) {
                Jwt jwt = (Jwt) principal;
                return jwt.getClaimAsString("email");
            } else {
                // Principal is not a JWT (e.g., admin authentication), so no email from JWT
                return null;
            }
        } catch (Exception e) {
            log.warn("Could not get current user email: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Gets or creates customer account from JWT token with proper synchronization
     * Uses retry mechanism to handle race conditions gracefully
     */
    public CustomerAccount getCurrentCustomerAccount() {
        try {
            Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
            if (!(principal instanceof Jwt)) {
                // Not a JWT principal, return null
                return null;
            }
            
            Jwt jwt = (Jwt) principal;
            String cognitoUserId = jwt.getClaimAsString("sub");
            String email = jwt.getClaimAsString("email");

            // Retry logic for handling race conditions
            for (int attempt = 1; attempt <= 3; attempt++) {
                try {
                    return getCurrentCustomerAccountAttempt(jwt, cognitoUserId, email);
                } catch (RuntimeException e) {
                    if (e.getMessage().contains("Account creation conflict") && attempt < 3) {
                        log.warn("Account creation conflict on attempt {}, retrying...", attempt);
                        try {
                            Thread.sleep(50 * attempt); // Backoff: 50ms, 100ms
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                        }
                        continue;
                    }
                    throw e;
                }
            }

            throw new RuntimeException("Failed to get/create customer account after 3 attempts");

        } catch (Exception e) {
            log.error("Error getting current customer account: {}", e.getMessage(), e);
            throw new RuntimeException("Could not get customer account", e);
        }
    }

    /**
     * Single attempt to get or create customer account
     */
    @Transactional
    private CustomerAccount getCurrentCustomerAccountAttempt(Jwt jwt, String cognitoUserId, String email) {
        // First, try to find existing account (most common case)
        CustomerAccount account = customerAccountRepository.findByCognitoUserId(cognitoUserId);

        if (account != null) {
            // Update last login and return existing account
            account.setLastLogin(LocalDateTime.now());
            return customerAccountRepository.save(account);
        }

        // Account doesn't exist, need to create one with synchronization
        return createAccountSafely(jwt, cognitoUserId, email);
    }

    /**
     * Safely creates a customer account with proper synchronization to handle race conditions
     */
    private CustomerAccount createAccountSafely(Jwt jwt, String cognitoUserId, String email) {
        // Use synchronized block to prevent race conditions
        synchronized (accountCreationLocks.computeIfAbsent(cognitoUserId, k -> new Object())) {
            try {
                // Double-check if account was created by another thread
                CustomerAccount existingAccount = customerAccountRepository.findByCognitoUserId(cognitoUserId);
                if (existingAccount != null) {
                    log.info("Account already exists for user: {} (created by another thread)", email);
                    existingAccount.setLastLogin(LocalDateTime.now());
                    return customerAccountRepository.save(existingAccount);
                }

                // Create new account
                CustomerAccount account = createAccountFromJwt(jwt);
                log.info("Created new customer account for user: {}", email);
                return account;

            } catch (DataIntegrityViolationException e) {
                // Handle the case where account was created between our checks
                log.warn("Duplicate key violation when creating account for {}, will throw exception to retry", email);

                // Throw a runtime exception that will trigger a retry with fresh session
                throw new RuntimeException("Account creation conflict - retry needed", e);

            } finally {
                // Clean up the lock object to prevent memory leaks
                accountCreationLocks.remove(cognitoUserId);
            }
        }
    }

    /**
     * Handle duplicate account scenario with a fresh transaction
     */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public CustomerAccount handleDuplicateAccount(String cognitoUserId, String email) {
        try {
            // Wait a moment for any other transaction to complete
            Thread.sleep(100);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }

        // Try to fetch by cognito user ID first
        CustomerAccount existingAccount = customerAccountRepository.findByCognitoUserId(cognitoUserId);
        if (existingAccount != null) {
            existingAccount.setLastLogin(LocalDateTime.now());
            return customerAccountRepository.save(existingAccount);
        }

        // If not found by cognito ID, try by email (in case there's an existing account without cognito ID)
        existingAccount = customerAccountRepository.findByEmail(email);
        if (existingAccount != null) {
            // Update the existing account with cognito user ID
            existingAccount.setCognitoUserId(cognitoUserId);
            existingAccount.setLastLogin(LocalDateTime.now());
            return customerAccountRepository.save(existingAccount);
        }

        // If still not found, there might be a timing issue
        throw new RuntimeException("Failed to create or find customer account for " + email);
    }

    /**
     * Creates customer account from JWT claims
     */
    private CustomerAccount createAccountFromJwt(Jwt jwt) {
        CustomerAccount account = new CustomerAccount();
        account.setCognitoUserId(jwt.getClaimAsString("sub"));
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

        // Award signup bonus for new accounts
        try {
            awardSignupBonusAsync(account.getId());
        } catch (Exception e) {
            log.warn("Could not award signup bonus for user {}: {}", account.getEmail(), e.getMessage());
        }

        return account;
    }

    /**
     * Award signup bonus asynchronously to avoid blocking the account creation
     */
    private void awardSignupBonusAsync(Long accountId) {
        if (rewardsService != null) {
            CompletableFuture.runAsync(() -> {
                try {
                    rewardsService.awardSignupBonus(accountId);
                } catch (Exception e) {
                    log.error("Error awarding signup bonus asynchronously: {}", e.getMessage(), e);
                }
            });
        } else {
            log.warn("RewardsService not available, cannot award signup bonus");
        }
    }

    /**
     * Check if customer account exists for the current user
     */
    public boolean customerAccountExists() {
        try {
            String cognitoUserId = getCurrentCognitoUserId();
            if (cognitoUserId == null) {
                return false;
            }
            return customerAccountRepository.findByCognitoUserId(cognitoUserId) != null;
        } catch (Exception e) {
            log.warn("Error checking if customer account exists: {}", e.getMessage());
            return false;
        }
    }
}