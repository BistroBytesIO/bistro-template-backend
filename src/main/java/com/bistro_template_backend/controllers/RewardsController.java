// File: src/main/java/com/bistro_template_backend/controllers/RewardsController.java
package com.bistro_template_backend.controllers;

import com.bistro_template_backend.models.CustomerAccount;
import com.bistro_template_backend.services.CognitoService;
import com.bistro_template_backend.services.RewardsService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/rewards")
@PreAuthorize("hasAuthority('SCOPE_openid')")
public class RewardsController {

    @Autowired
    private RewardsService rewardsService;

    @Autowired
    private CognitoService cognitoService;

    /**
     * Gets current customer's rewards status
     */
    @GetMapping("/status")
    public ResponseEntity<?> getRewardsStatus() {
        try {
            CustomerAccount account = cognitoService.getCurrentCustomerAccount();
            Map<String, Object> status = rewardsService.getRewardsStatus(account.getId());
            return ResponseEntity.ok(status);

        } catch (Exception e) {
            log.error("Error getting rewards status: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Redeems points for a free menu item
     */
    @PostMapping("/redeem/item/{menuItemId}")
    public ResponseEntity<?> redeemForMenuItem(@PathVariable Long menuItemId) {
        try {
            CustomerAccount account = cognitoService.getCurrentCustomerAccount();
            Map<String, Object> result = rewardsService.redeemPointsForMenuItem(account.getId(), menuItemId);
            return ResponseEntity.ok(result);

        } catch (Exception e) {
            log.error("Error redeeming points for menu item: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }


    /**
     * Gets available menu items that can be redeemed with points
     */
    @GetMapping("/redeemable-items")
    public ResponseEntity<?> getRedeemableItems() {
        try {
            CustomerAccount account = cognitoService.getCurrentCustomerAccount();
            return ResponseEntity.ok(rewardsService.getRedeemableItems(account.getId()));

        } catch (Exception e) {
            log.error("Error getting redeemable items: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Validates a redemption code
     */
    @PostMapping("/validate")
    public ResponseEntity<?> validateRedemptionCode(@RequestBody Map<String, String> request) {
        try {
            String redemptionCode = request.get("redemptionCode");

            if (redemptionCode == null || redemptionCode.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Redemption code is required"));
            }

            Map<String, Object> result = rewardsService.validateRedemptionCode(redemptionCode);
            return ResponseEntity.ok(result);

        } catch (Exception e) {
            log.error("Error validating redemption code: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Gets rewards analytics for the customer
     */
    @GetMapping("/analytics")
    public ResponseEntity<?> getRewardsAnalytics() {
        try {
            CustomerAccount account = cognitoService.getCurrentCustomerAccount();
            Map<String, Object> analytics = rewardsService.getRewardsAnalytics(account.getId());
            return ResponseEntity.ok(analytics);

        } catch (Exception e) {
            log.error("Error getting rewards analytics: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Gets list of redeemed rewards for the customer
     */
    @GetMapping("/redeemed")
    public ResponseEntity<?> getRedeemedRewards() {
        try {
            CustomerAccount account = cognitoService.getCurrentCustomerAccount();
            return ResponseEntity.ok(rewardsService.getRedeemedRewards(account.getId()));

        } catch (Exception e) {
            log.error("Error getting redeemed rewards: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Uses a redeemed reward (decrements available quantity)
     */
    @PostMapping("/use")
    public ResponseEntity<?> useRedeemedReward(@RequestBody Map<String, Long> request) {
        try {
            CustomerAccount account = cognitoService.getCurrentCustomerAccount();
            Long redeemedItemId = request.get("redeemedItemId");

            if (redeemedItemId == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "Redeemed item ID is required"));
            }

            return ResponseEntity.ok(rewardsService.useRedeemedReward(account.getId(), redeemedItemId));

        } catch (Exception e) {
            log.error("Error using redeemed reward: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Returns a redeemed reward item back to the available pool
     */
    @PostMapping("/return/{redeemedItemId}")
    public ResponseEntity<?> returnRewardItem(@PathVariable Long redeemedItemId) {
        try {
            CustomerAccount account = cognitoService.getCurrentCustomerAccount();
            rewardsService.returnRewardItem(account.getId(), redeemedItemId);
            return ResponseEntity.ok(Map.of("success", true, "message", "Reward item returned successfully"));

        } catch (Exception e) {
            log.error("Error returning reward item: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}