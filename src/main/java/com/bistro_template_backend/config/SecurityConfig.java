// src/main/java/com/bistro_template_backend/config/SecurityConfig.java
package com.bistro_template_backend.config;

import com.bistro_template_backend.utils.JwtFilter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Autowired
    private CognitoConfig cognitoConfig;

//    private final JwtFilter jwtFilter;
//
//    public SecurityConfig(JwtFilter jwtFilter) {
//        this.jwtFilter = jwtFilter;
//    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(csrf -> csrf.disable())
                .requiresChannel(channel ->
                        channel.requestMatchers(r -> r.getHeader("X-Forwarded-Proto") != null)
                                .requiresSecure())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/menu/**").permitAll()
                        .requestMatchers("/api/categories/**").permitAll()
                        .requestMatchers("/api/orders/*/pay/**").permitAll()
                        .requestMatchers("/api/orders/*/confirmPayment/**").permitAll()
                        .requestMatchers("/api/orders/create").permitAll()
                        .requestMatchers("/api/orders").permitAll()
                        .requestMatchers("/api/auth/**").permitAll()
                        .requestMatchers("/webhooks/**").permitAll()
                        .requestMatchers("/api/websocket/**").permitAll()
                        .requestMatchers("/api/test/**").permitAll()
                        .requestMatchers("/actuator/**").permitAll() // Add health check endpoint
                        // Protected endpoints (require authentication)
                        .requestMatchers("/api/customer/**").authenticated()
                        .requestMatchers("/api/rewards/**").authenticated()
                        .requestMatchers("/api/profile/**").authenticated()
                        // Admin endpoints (keep existing protection)
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        // Allow all other requests (maintain existing behavior)
                        .anyRequest().permitAll()
                )
//                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.decoder(jwtDecoder()))
                );;

        return http.build();
    }

    @Bean
    public JwtDecoder jwtDecoder() {
        return NimbusJwtDecoder.withJwkSetUri(cognitoConfig.getJwkUrl()).build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(Arrays.asList("*"));
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(Arrays.asList("*"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

//    @Bean
//    public CorsConfigurationSource corsConfigurationSource() {
//        CorsConfiguration configuration = new CorsConfiguration();
//
//        // Use origin patterns for more flexible matching
//        configuration.setAllowedOriginPatterns(List.of(
//                "http://localhost:*",
//                "https://localhost:*",
//                "http://192.168.*.*:*",
//                "https://192.168.*.*:*",
//                "http://10.0.*.*:*",
//                "https://10.0.*.*:*",
//                "https://*.ngrok-free.app",
//                "https://*.ngrok.io"
//        ));
//
//        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
//        configuration.setAllowedHeaders(Arrays.asList("*"));
//
//        // Set to false for broader mobile compatibility
//        configuration.setAllowCredentials(false);
//
//        configuration.setMaxAge(3600L);
//
//        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
//        source.registerCorsConfiguration("/**", configuration);
//        return source;
//    }
}