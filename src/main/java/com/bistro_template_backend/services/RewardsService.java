// File: src/main/java/com/bistro_template_backend/services/RewardsService.java
package com.bistro_template_backend.services;

import com.bistro_template_backend.models.*;
import com.bistro_template_backend.repositories.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

@Slf4j
@Service
public class RewardsService {

    @Autowired
    private CustomerAccountRepository customerAccountRepository;

    @Autowired
    private RewardTransactionRepository rewardTransactionRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private MenuItemRepository menuItemRepository;

    @Value("${rewards.points.per.dollar:5}")
    private int pointsPerDollar;

    @Value("${rewards.signup.bonus:100}")
    private int signupBonus;

    @Value("${rewards.birthday.bonus:50}")
    private int birthdayBonus;

    /**
     * Awards signup bonus to new customer
     */
    @Transactional
    public void awardSignupBonus(Long customerAccountId) {
        CustomerAccount account = customerAccountRepository.findById(customerAccountId)
                .orElseThrow(() -> new RuntimeException("Customer account not found"));

        if (Boolean.TRUE.equals(account.getSignupBonusAwarded())) {
            return; // Already awarded
        }

        // Award signup bonus
        account.setTotalRewardPoints(account.getTotalRewardPoints() + signupBonus);
        account.setAvailableRewardPoints(account.getAvailableRewardPoints() + signupBonus);
        account.setSignupBonusAwarded(true);
        customerAccountRepository.save(account);

        // Record transaction
        RewardTransaction transaction = new RewardTransaction();
        transaction.setCustomerAccountId(customerAccountId);
        transaction.setTransactionType("BONUS");
        transaction.setPointsAmount(signupBonus);
        transaction.setDescription("Welcome bonus for signing up!");
        rewardTransactionRepository.save(transaction);

        log.info("Awarded {} signup bonus points to customer {}", signupBonus, account.getEmail());
    }

    /**
     * Awards points for an order
     */
    @Transactional
    public void awardPointsForOrder(Long customerAccountId, Long orderId) {
        try {
            CustomerAccount account = customerAccountRepository.findById(customerAccountId)
                    .orElseThrow(() -> new RuntimeException("Customer account not found"));

            Order order = orderRepository.findById(orderId)
                    .orElseThrow(() -> new RuntimeException("Order not found"));

            // Check if points already awarded for this order
            RewardTransaction existing = rewardTransactionRepository.findByOrderIdAndTransactionType(orderId, "EARNED");
            if (existing != null) {
                log.warn("Points already awarded for order {}", orderId);
                return;
            }

            // Calculate points (5 points per dollar spent)
            BigDecimal orderAmount = order.getTotalAmount();
            int pointsEarned = orderAmount.multiply(new BigDecimal(pointsPerDollar)).intValue();

            // Update account
            account.setTotalRewardPoints(account.getTotalRewardPoints() + pointsEarned);
            account.setAvailableRewardPoints(account.getAvailableRewardPoints() + pointsEarned);
            account.setLifetimeSpent(account.getLifetimeSpent().add(orderAmount));
            customerAccountRepository.save(account);

            // Record transaction
            RewardTransaction transaction = new RewardTransaction();
            transaction.setCustomerAccountId(customerAccountId);
            transaction.setOrderId(orderId);
            transaction.setTransactionType("EARNED");
            transaction.setPointsAmount(pointsEarned);
            transaction.setDollarAmount(orderAmount);
            transaction.setDescription("Points earned from order #" + orderId);
            rewardTransactionRepository.save(transaction);

            log.info("Awarded {} points to customer {} for order {} (${} spent)",
                    pointsEarned, account.getEmail(), orderId, orderAmount);

        } catch (Exception e) {
            log.error("Error awarding points for order {}: {}", orderId, e.getMessage(), e);
        }
    }

    /**
     * Gets customer's rewards status
     */
    public Map<String, Object> getRewardsStatus(Long customerAccountId) {
        CustomerAccount account = customerAccountRepository.findById(customerAccountId)
                .orElseThrow(() -> new RuntimeException("Customer account not found"));

        List<RewardTransaction> recentTransactions = rewardTransactionRepository
                .findByCustomerAccountIdOrderByCreatedAtDesc(customerAccountId, 10);

        Map<String, Object> status = new HashMap<>();
        status.put("totalPoints", account.getTotalRewardPoints());
        status.put("availablePoints", account.getAvailableRewardPoints());
        status.put("lifetimeSpent", account.getLifetimeSpent());
        status.put("pointsPerDollar", pointsPerDollar);
        status.put("dollarValue", calculateDollarValue(account.getAvailableRewardPoints()));
        status.put("recentTransactions", formatTransactions(recentTransactions));

        return status;
    }

    /**
     * Redeems points for a free menu item
     */
    @Transactional
    public Map<String, Object> redeemPointsForMenuItem(Long customerAccountId, Long menuItemId) {
        CustomerAccount account = customerAccountRepository.findById(customerAccountId)
                .orElseThrow(() -> new RuntimeException("Customer account not found"));

        MenuItem menuItem = menuItemRepository.findById(menuItemId)
                .orElseThrow(() -> new RuntimeException("Menu item not found"));

        // Check if it's a reward item
        if (!Boolean.TRUE.equals(menuItem.getIsRewardItem())) {
            throw new RuntimeException("This item is not available for point redemption");
        }

        // Calculate points needed
        int pointsNeeded = menuItem.getPointsToRedeem();

        if (account.getAvailableRewardPoints() < pointsNeeded) {
            throw new RuntimeException("Insufficient points. You need " + pointsNeeded +
                    " points but only have " + account.getAvailableRewardPoints());
        }

        // Generate redemption code
        String redemptionCode = generateRedemptionCode();

        // Update account
        account.setAvailableRewardPoints(account.getAvailableRewardPoints() - pointsNeeded);
        customerAccountRepository.save(account);

        // Record transaction
        RewardTransaction transaction = new RewardTransaction();
        transaction.setCustomerAccountId(customerAccountId);
        transaction.setTransactionType("REDEEMED");
        transaction.setPointsAmount(-pointsNeeded);
        transaction.setDollarAmount(menuItem.getPrice());
        transaction.setDescription("Redeemed points for free " + menuItem.getName());
        transaction.setReferenceId(redemptionCode);
        rewardTransactionRepository.save(transaction);

        Map<String, Object> result = new HashMap<>();
        result.put("redemptionCode", redemptionCode);
        result.put("pointsRedeemed", pointsNeeded);
        result.put("menuItem", Map.of(
                "id", menuItem.getId(),
                "name", menuItem.getName(),
                "price", menuItem.getPrice()
        ));
        result.put("remainingPoints", account.getAvailableRewardPoints());

        log.info("Customer {} redeemed {} points for free {} (code: {})",
                account.getEmail(), pointsNeeded, menuItem.getName(), redemptionCode);

        return result;
    }

    /**
     * Redeems points for a discount
     */
    @Transactional
    public Map<String, Object> redeemPointsForDiscount(Long customerAccountId, int pointsToRedeem) {
        CustomerAccount account = customerAccountRepository.findById(customerAccountId)
                .orElseThrow(() -> new RuntimeException("Customer account not found"));

        if (account.getAvailableRewardPoints() < pointsToRedeem) {
            throw new RuntimeException("Insufficient points available. You have " +
                    account.getAvailableRewardPoints() + " points, but need " + pointsToRedeem);
        }

        // Calculate dollar value (100 points = $1.00)
        BigDecimal dollarValue = new BigDecimal(pointsToRedeem).divide(new BigDecimal(100), 2, RoundingMode.HALF_UP);

        // Generate redemption code
        String redemptionCode = generateRedemptionCode();

        // Update account
        account.setAvailableRewardPoints(account.getAvailableRewardPoints() - pointsToRedeem);
        customerAccountRepository.save(account);

        // Record transaction
        RewardTransaction transaction = new RewardTransaction();
        transaction.setCustomerAccountId(customerAccountId);
        transaction.setTransactionType("REDEEMED");
        transaction.setPointsAmount(-pointsToRedeem); // Negative for redemption
        transaction.setDollarAmount(dollarValue);
        transaction.setDescription("Redeemed points for $" + dollarValue + " discount");
        transaction.setReferenceId(redemptionCode);
        rewardTransactionRepository.save(transaction);

        Map<String, Object> result = new HashMap<>();
        result.put("redemptionCode", redemptionCode);
        result.put("pointsRedeemed", pointsToRedeem);
        result.put("dollarValue", dollarValue);
        result.put("remainingPoints", account.getAvailableRewardPoints());

        log.info("Customer {} redeemed {} points for ${} (code: {})",
                account.getEmail(), pointsToRedeem, dollarValue, redemptionCode);

        return result;
    }

    /**
     * Gets available menu items that can be redeemed with points
     */
    public List<Map<String, Object>> getRedeemableItems(Long customerAccountId) {
        CustomerAccount account = customerAccountRepository.findById(customerAccountId)
                .orElseThrow(() -> new RuntimeException("Customer account not found"));

        List<MenuItem> rewardItems = menuItemRepository.findByIsRewardItemTrueOrderByPointsToRedeemAsc();
        int availablePoints = account.getAvailableRewardPoints();

        return rewardItems.stream()
                .map(item -> {
                    int pointsNeeded = item.getPointsToRedeem();
                    boolean canRedeem = availablePoints >= pointsNeeded;

                    Map<String, Object> redeemableItem = new HashMap<>();
                    redeemableItem.put("id", item.getId());
                    redeemableItem.put("name", item.getName());
                    redeemableItem.put("description", item.getDescription());
                    redeemableItem.put("price", item.getPrice());
                    redeemableItem.put("pointsToRedeem", pointsNeeded);
                    redeemableItem.put("canRedeem", canRedeem);
                    redeemableItem.put("imageUrl", item.getImageUrl());

                    return redeemableItem;
                })
                .toList();
    }

    /**
     * Validates and applies a redemption code during checkout
     */
    @Transactional
    public Map<String, Object> validateRedemptionCode(String redemptionCode) {
        RewardTransaction transaction = rewardTransactionRepository.findByReferenceId(redemptionCode);

        if (transaction == null) {
            throw new RuntimeException("Invalid redemption code");
        }

        if (!"REDEEMED".equals(transaction.getTransactionType())) {
            throw new RuntimeException("Redemption code is not valid");
        }

        Map<String, Object> redemption = new HashMap<>();
        redemption.put("valid", true);
        redemption.put("dollarValue", transaction.getDollarAmount());
        redemption.put("description", transaction.getDescription());
        redemption.put("customerAccountId", transaction.getCustomerAccountId());

        return redemption;
    }

    /**
     * Gets customer rewards analytics
     */
    public Map<String, Object> getRewardsAnalytics(Long customerAccountId) {
        CustomerAccount account = customerAccountRepository.findById(customerAccountId)
                .orElseThrow(() -> new RuntimeException("Customer account not found"));

        List<RewardTransaction> allTransactions = rewardTransactionRepository
                .findByCustomerAccountIdOrderByCreatedAtDesc(customerAccountId, null);

        int totalEarned = allTransactions.stream()
                .filter(t -> "EARNED".equals(t.getTransactionType()) || "BONUS".equals(t.getTransactionType()))
                .mapToInt(RewardTransaction::getPointsAmount)
                .sum();

        int totalRedeemed = Math.abs(allTransactions.stream()
                .filter(t -> "REDEEMED".equals(t.getTransactionType()))
                .mapToInt(RewardTransaction::getPointsAmount)
                .sum());

        BigDecimal totalSaved = allTransactions.stream()
                .filter(t -> "REDEEMED".equals(t.getTransactionType()))
                .map(RewardTransaction::getDollarAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<String, Object> analytics = new HashMap<>();
        analytics.put("totalPointsEarned", totalEarned);
        analytics.put("totalPointsRedeemed", totalRedeemed);
        analytics.put("totalMoneySaved", totalSaved);
        analytics.put("lifetimeSpent", account.getLifetimeSpent());
        analytics.put("memberSince", account.getCreatedAt());
        analytics.put("redemptionCount", (int) allTransactions.stream()
                .filter(t -> "REDEEMED".equals(t.getTransactionType()))
                .count());

        return analytics;
    }

    private BigDecimal calculateDollarValue(int points) {
        return new BigDecimal(points).divide(new BigDecimal(100), 2, RoundingMode.HALF_UP);
    }

    private List<Map<String, Object>> formatTransactions(List<RewardTransaction> transactions) {
        return transactions.stream()
                .map(transaction -> {
                    Map<String, Object> formatted = new HashMap<>();
                    formatted.put("id", transaction.getId());
                    formatted.put("type", transaction.getTransactionType());
                    formatted.put("points", transaction.getPointsAmount());
                    formatted.put("description", transaction.getDescription());
                    formatted.put("date", transaction.getCreatedAt());
                    formatted.put("orderId", transaction.getOrderId());
                    return formatted;
                })
                .toList();
    }

    private String generateRedemptionCode() {
        return "REWARD" + System.currentTimeMillis() +
                UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase();
    }
}