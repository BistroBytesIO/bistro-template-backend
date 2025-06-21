package com.bistro_template_backend.controllers;

import com.bistro_template_backend.dto.AuthRequest;
import com.bistro_template_backend.models.CustomerAccount;
import com.bistro_template_backend.models.User;
import com.bistro_template_backend.repositories.UserRepository;
import com.bistro_template_backend.services.CognitoService;
import com.bistro_template_backend.utils.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private CognitoService cognitoService;

    /**
     * Test endpoint to verify authentication is working
     */
    @GetMapping("/me")
    @PreAuthorize("hasAuthority('SCOPE_openid')")
    public ResponseEntity<Map<String, Object>> getCurrentUser() {
        try {
            String cognitoUserId = cognitoService.getCurrentCognitoUserId();
            String email = cognitoService.getCurrentUserEmail();
            CustomerAccount account = cognitoService.getCurrentCustomerAccount();

            Map<String, Object> user = new HashMap<>();
            user.put("cognitoUserId", cognitoUserId);
            user.put("email", email);
            user.put("accountId", account.getId());
            user.put("firstName", account.getFirstName());
            user.put("authenticated", true);

            return ResponseEntity.ok(user);

        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Not authenticated"));
        }
    }

    /**
     * Public endpoint for testing
     */
    @GetMapping("/public")
    public ResponseEntity<Map<String, String>> publicEndpoint() {
        return ResponseEntity.ok(Map.of("message", "This is a public endpoint"));
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody AuthRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid credentials");
        }

        // Generate JWT token
        String token = jwtUtil.generateToken(user.getEmail(), user.getRole());

        Map<String, Object> response = new HashMap<>();
        response.put("email", user.getEmail());
        response.put("role", user.getRole());
        response.put("token", token);

        return ResponseEntity.ok(response);
    }

    @PostMapping("/create-admin")
    public ResponseEntity<?> createAdminUser(@RequestBody AuthRequest request) {
        User admin = new User();
        admin.setEmail(request.getEmail());
        admin.setPassword(passwordEncoder.encode(request.getPassword()));
        admin.setRole("ROLE_ADMIN");
        userRepository.save(admin);
        return ResponseEntity.ok("Admin user created successfully");
    }
}
