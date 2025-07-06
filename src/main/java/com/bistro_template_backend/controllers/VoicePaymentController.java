package com.bistro_template_backend.controllers;

import com.bistro_template_backend.models.Order;
import com.bistro_template_backend.repositories.OrderRepository;
import com.bistro_template_backend.services.VoicePaymentService;
import com.bistro_template_backend.services.VoiceSetupIntentService;
import com.bistro_template_backend.services.OffSessionPaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;

@RestController
@RequestMapping("/api/voice/payments")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
public class VoicePaymentController {

    private final VoicePaymentService voicePaymentService;
    private final VoiceSetupIntentService voiceSetupIntentService;
    private final OffSessionPaymentService offSessionPaymentService;
    private final OrderRepository orderRepository;

    /**
     * Get all saved payment methods for a customer
     * GET /api/voice/payments/methods?customerEmail=user@example.com
     */
    @GetMapping("/methods")
    public ResponseEntity<Map<String, Object>> getPaymentMethods(
            @RequestParam String customerEmail) {
        
        log.info("Getting payment methods for customer: {}", customerEmail);
        
        try {
            Map<String, Object> result = voicePaymentService.getCustomerPaymentMethods(customerEmail);
            
            if ((Boolean) result.get("success")) {
                return ResponseEntity.ok(result);
            } else {
                return ResponseEntity.badRequest().body(result);
            }
            
        } catch (Exception e) {
            log.error("Error getting payment methods for customer: {}", customerEmail, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("success", false, "error", "Internal server error"));
        }
    }

    /**
     * Create a setup intent for storing payment methods
     * POST /api/voice/payments/setup-intent
     */
    @PostMapping("/setup-intent")
    public ResponseEntity<Map<String, Object>> createSetupIntent(
            @RequestBody Map<String, String> request) {
        
        String customerEmail = request.get("customerEmail");
        log.info("Creating setup intent for customer: {}", customerEmail);
        
        if (customerEmail == null || customerEmail.trim().isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "error", "Customer email is required"));
        }
        
        try {
            Map<String, Object> result = voicePaymentService.createSetupIntent(customerEmail);
            
            if ((Boolean) result.get("success")) {
                return ResponseEntity.ok(result);
            } else {
                return ResponseEntity.badRequest().body(result);
            }
            
        } catch (Exception e) {
            log.error("Error creating setup intent for customer: {}", customerEmail, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("success", false, "error", "Internal server error"));
        }
    }

    /**
     * Process voice payment for an order
     * POST /api/voice/payments/process
     */
    @PostMapping("/process")
    public ResponseEntity<Map<String, Object>> processVoicePayment(
            @RequestBody Map<String, Object> request) {
        
        String customerEmail = (String) request.get("customerEmail");
        Long orderId = Long.valueOf(request.get("orderId").toString());
        String paymentMethodId = (String) request.get("paymentMethodId");
        
        log.info("Processing voice payment - Customer: {}, Order: {}, PaymentMethod: {}", 
                customerEmail, orderId, paymentMethodId);
        
        // Validate required fields
        if (customerEmail == null || orderId == null || paymentMethodId == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "error", "Missing required fields"));
        }
        
        try {
            // Find the order
            Order order = orderRepository.findById(orderId)
                    .orElseThrow(() -> new RuntimeException("Order not found"));
            
            Map<String, Object> result = voicePaymentService.processVoicePayment(
                    customerEmail, order, paymentMethodId);
            
            if ((Boolean) result.get("success")) {
                return ResponseEntity.ok(result);
            } else {
                return ResponseEntity.badRequest().body(result);
            }
            
        } catch (Exception e) {
            log.error("Error processing voice payment - Order: {}", orderId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("success", false, "error", "Payment processing failed"));
        }
    }

    /**
     * Handle authentication required scenarios
     * POST /api/voice/payments/handle-auth
     */
    @PostMapping("/handle-auth")
    public ResponseEntity<Map<String, Object>> handleAuthenticationRequired(
            @RequestBody Map<String, String> request) {
        
        String paymentIntentId = request.get("paymentIntentId");
        log.info("Handling authentication required for payment intent: {}", paymentIntentId);
        
        if (paymentIntentId == null || paymentIntentId.trim().isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "error", "Payment intent ID is required"));
        }
        
        try {
            Map<String, Object> result = voicePaymentService.handleAuthenticationRequired(paymentIntentId);
            
            if ((Boolean) result.get("success")) {
                return ResponseEntity.ok(result);
            } else {
                return ResponseEntity.badRequest().body(result);
            }
            
        } catch (Exception e) {
            log.error("Error handling authentication for payment intent: {}", paymentIntentId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("success", false, "error", "Authentication handling failed"));
        }
    }

    /**
     * Get payment method recommendations for voice interface
     * GET /api/voice/payments/recommendations?customerEmail=user@example.com
     */
    @GetMapping("/recommendations")
    public ResponseEntity<Map<String, Object>> getVoicePaymentRecommendations(
            @RequestParam String customerEmail,
            HttpServletRequest request) {
        
        String userAgent = request.getHeader("User-Agent");
        log.info("Getting voice payment recommendations for customer: {}", customerEmail);
        
        try {
            Map<String, Object> result = voicePaymentService.getVoicePaymentRecommendations(
                    customerEmail, userAgent);
            
            if ((Boolean) result.get("success")) {
                return ResponseEntity.ok(result);
            } else {
                return ResponseEntity.badRequest().body(result);
            }
            
        } catch (Exception e) {
            log.error("Error getting voice payment recommendations for customer: {}", customerEmail, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("success", false, "error", "Internal server error"));
        }
    }

    /**
     * Get payment capabilities for current device/browser
     * GET /api/voice/payments/capabilities
     */
    @GetMapping("/capabilities")
    public ResponseEntity<Map<String, Object>> getPaymentCapabilities(
            HttpServletRequest request) {
        
        String userAgent = request.getHeader("User-Agent");
        
        Map<String, Object> capabilities = Map.of(
            "success", true,
            "userAgent", userAgent != null ? userAgent : "",
            "supportsApplePay", userAgent != null && (userAgent.toLowerCase().contains("iphone") || 
                                                     userAgent.toLowerCase().contains("ipad") ||
                                                     userAgent.toLowerCase().contains("safari")),
            "supportsGooglePay", userAgent != null && (userAgent.toLowerCase().contains("android") ||
                                                      userAgent.toLowerCase().contains("chrome")),
            "isMobile", userAgent != null && (userAgent.toLowerCase().contains("mobile") ||
                                             userAgent.toLowerCase().contains("android") ||
                                             userAgent.toLowerCase().contains("iphone"))
        );
        
        return ResponseEntity.ok(capabilities);
    }

    /**
     * Create enhanced setup intent for voice payments
     * POST /api/voice/payments/setup-intent/enhanced
     */
    @PostMapping("/setup-intent/enhanced")
    public ResponseEntity<Map<String, Object>> createEnhancedSetupIntent(
            @RequestBody Map<String, String> request,
            HttpServletRequest httpRequest) {
        
        String customerEmail = request.get("customerEmail");
        String paymentMethodType = request.getOrDefault("paymentMethodType", "card");
        String deviceInfo = httpRequest.getHeader("User-Agent");
        
        log.info("Creating enhanced setup intent for customer: {} with type: {}", 
                customerEmail, paymentMethodType);
        
        if (customerEmail == null || customerEmail.trim().isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "error", "Customer email is required"));
        }
        
        try {
            Map<String, Object> result = voiceSetupIntentService.createVoiceOptimizedSetupIntent(
                    customerEmail, paymentMethodType, deviceInfo);
            
            if ((Boolean) result.get("success")) {
                return ResponseEntity.ok(result);
            } else {
                return ResponseEntity.badRequest().body(result);
            }
            
        } catch (Exception e) {
            log.error("Error creating enhanced setup intent for customer: {}", customerEmail, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("success", false, "error", "Internal server error"));
        }
    }

    /**
     * Create Apple Pay setup intent
     * POST /api/voice/payments/setup-intent/apple-pay
     */
    @PostMapping("/setup-intent/apple-pay")
    public ResponseEntity<Map<String, Object>> createApplePaySetupIntent(
            @RequestBody Map<String, String> request) {
        
        String customerEmail = request.get("customerEmail");
        log.info("Creating Apple Pay setup intent for customer: {}", customerEmail);
        
        if (customerEmail == null || customerEmail.trim().isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "error", "Customer email is required"));
        }
        
        try {
            Map<String, Object> result = voiceSetupIntentService.createApplePaySetupIntent(customerEmail);
            
            if ((Boolean) result.get("success")) {
                return ResponseEntity.ok(result);
            } else {
                return ResponseEntity.badRequest().body(result);
            }
            
        } catch (Exception e) {
            log.error("Error creating Apple Pay setup intent for customer: {}", customerEmail, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("success", false, "error", "Internal server error"));
        }
    }

    /**
     * Create Google Pay setup intent
     * POST /api/voice/payments/setup-intent/google-pay
     */
    @PostMapping("/setup-intent/google-pay")
    public ResponseEntity<Map<String, Object>> createGooglePaySetupIntent(
            @RequestBody Map<String, String> request) {
        
        String customerEmail = request.get("customerEmail");
        log.info("Creating Google Pay setup intent for customer: {}", customerEmail);
        
        if (customerEmail == null || customerEmail.trim().isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "error", "Customer email is required"));
        }
        
        try {
            Map<String, Object> result = voiceSetupIntentService.createGooglePaySetupIntent(customerEmail);
            
            if ((Boolean) result.get("success")) {
                return ResponseEntity.ok(result);
            } else {
                return ResponseEntity.badRequest().body(result);
            }
            
        } catch (Exception e) {
            log.error("Error creating Google Pay setup intent for customer: {}", customerEmail, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("success", false, "error", "Internal server error"));
        }
    }

    /**
     * Confirm setup intent completion
     * POST /api/voice/payments/setup-intent/confirm
     */
    @PostMapping("/setup-intent/confirm")
    public ResponseEntity<Map<String, Object>> confirmSetupIntentCompletion(
            @RequestBody Map<String, String> request) {
        
        String setupIntentId = request.get("setupIntentId");
        String customerEmail = request.get("customerEmail");
        
        log.info("Confirming setup intent completion: {} for customer: {}", setupIntentId, customerEmail);
        
        if (setupIntentId == null || customerEmail == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "error", "Setup intent ID and customer email are required"));
        }
        
        try {
            Map<String, Object> result = voiceSetupIntentService.confirmSetupIntentCompletion(
                    setupIntentId, customerEmail);
            
            if ((Boolean) result.get("success")) {
                return ResponseEntity.ok(result);
            } else {
                return ResponseEntity.badRequest().body(result);
            }
            
        } catch (Exception e) {
            log.error("Error confirming setup intent: {}", setupIntentId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("success", false, "error", "Internal server error"));
        }
    }

    /**
     * Get setup intent status
     * GET /api/voice/payments/setup-intent/{setupIntentId}/status
     */
    @GetMapping("/setup-intent/{setupIntentId}/status")
    public ResponseEntity<Map<String, Object>> getSetupIntentStatus(
            @PathVariable String setupIntentId) {
        
        log.info("Getting setup intent status: {}", setupIntentId);
        
        try {
            Map<String, Object> result = voiceSetupIntentService.getSetupIntentStatus(setupIntentId);
            
            if ((Boolean) result.get("success")) {
                return ResponseEntity.ok(result);
            } else {
                return ResponseEntity.badRequest().body(result);
            }
            
        } catch (Exception e) {
            log.error("Error getting setup intent status: {}", setupIntentId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("success", false, "error", "Internal server error"));
        }
    }

    /**
     * Process off-session payment with specific payment method type
     * POST /api/voice/payments/off-session/by-type
     */
    @PostMapping("/off-session/by-type")
    public ResponseEntity<Map<String, Object>> processOffSessionPaymentByType(
            @RequestBody Map<String, Object> request) {
        
        String customerEmail = (String) request.get("customerEmail");
        Long orderId = Long.valueOf(request.get("orderId").toString());
        String paymentMethodType = (String) request.get("paymentMethodType");
        
        log.info("Processing off-session {} payment - Customer: {}, Order: {}", 
                paymentMethodType, customerEmail, orderId);
        
        if (customerEmail == null || orderId == null || paymentMethodType == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "error", "Missing required fields"));
        }
        
        try {
            Order order = orderRepository.findById(orderId)
                    .orElseThrow(() -> new RuntimeException("Order not found"));
            
            Map<String, Object> result = offSessionPaymentService.processOffSessionPaymentWithType(
                    customerEmail, order, paymentMethodType);
            
            if ((Boolean) result.get("success")) {
                return ResponseEntity.ok(result);
            } else {
                return ResponseEntity.badRequest().body(result);
            }
            
        } catch (Exception e) {
            log.error("Error processing off-session {} payment - Order: {}", paymentMethodType, orderId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("success", false, "error", "Payment processing failed"));
        }
    }

    /**
     * Retry failed off-session payment
     * POST /api/voice/payments/off-session/retry
     */
    @PostMapping("/off-session/retry")
    public ResponseEntity<Map<String, Object>> retryOffSessionPayment(
            @RequestBody Map<String, String> request) {
        
        String originalPaymentIntentId = request.get("originalPaymentIntentId");
        String alternativePaymentMethodId = request.get("alternativePaymentMethodId");
        
        log.info("Retrying off-session payment - Original: {}, Alternative method: {}", 
                originalPaymentIntentId, alternativePaymentMethodId);
        
        if (originalPaymentIntentId == null || alternativePaymentMethodId == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "error", "Missing required fields"));
        }
        
        try {
            Map<String, Object> result = offSessionPaymentService.retryOffSessionPayment(
                    originalPaymentIntentId, alternativePaymentMethodId);
            
            if ((Boolean) result.get("success")) {
                return ResponseEntity.ok(result);
            } else {
                return ResponseEntity.badRequest().body(result);
            }
            
        } catch (Exception e) {
            log.error("Error retrying off-session payment: {}", originalPaymentIntentId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("success", false, "error", "Payment retry failed"));
        }
    }

    /**
     * Get off-session payment recommendations
     * GET /api/voice/payments/off-session/recommendations?customerEmail=user@example.com
     */
    @GetMapping("/off-session/recommendations")
    public ResponseEntity<Map<String, Object>> getOffSessionPaymentRecommendations(
            @RequestParam String customerEmail,
            HttpServletRequest request) {
        
        String deviceInfo = request.getHeader("User-Agent");
        log.info("Getting off-session payment recommendations for customer: {}", customerEmail);
        
        try {
            Map<String, Object> result = offSessionPaymentService.getOffSessionPaymentRecommendations(
                    customerEmail, deviceInfo);
            
            if ((Boolean) result.get("success")) {
                return ResponseEntity.ok(result);
            } else {
                return ResponseEntity.badRequest().body(result);
            }
            
        } catch (Exception e) {
            log.error("Error getting off-session payment recommendations for customer: {}", customerEmail, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("success", false, "error", "Internal server error"));
        }
    }

    /**
     * Validate off-session payment eligibility
     * POST /api/voice/payments/off-session/validate
     */
    @PostMapping("/off-session/validate")
    public ResponseEntity<Map<String, Object>> validateOffSessionEligibility(
            @RequestBody Map<String, String> request) {
        
        String customerEmail = request.get("customerEmail");
        String paymentMethodId = request.get("paymentMethodId");
        
        log.info("Validating off-session eligibility - Customer: {}, PaymentMethod: {}", 
                customerEmail, paymentMethodId);
        
        if (customerEmail == null || paymentMethodId == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "error", "Missing required fields"));
        }
        
        try {
            Map<String, Object> result = offSessionPaymentService.validateOffSessionEligibility(
                    customerEmail, paymentMethodId);
            
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            log.error("Error validating off-session eligibility: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("success", false, "error", "Validation failed"));
        }
    }

    /**
     * Health check endpoint for voice payment system
     * GET /api/voice/payments/health
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> healthCheck() {
        return ResponseEntity.ok(Map.of(
            "status", "healthy",
            "service", "voice-payment",
            "timestamp", System.currentTimeMillis()
        ));
    }
}