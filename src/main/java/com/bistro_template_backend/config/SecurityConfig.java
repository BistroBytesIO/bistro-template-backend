// src/main/java/com/bistro_template_backend/config/SecurityConfig.java
package com.bistro_template_backend.config;

import com.bistro_template_backend.utils.JwtFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
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

    private final JwtFilter jwtFilter;

    public SecurityConfig(JwtFilter jwtFilter) {
        this.jwtFilter = jwtFilter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(csrf -> csrf.disable())
                .requiresChannel(channel ->
                        channel.requestMatchers(r -> r.getHeader("X-Forwarded-Proto") != null)
                                .requiresSecure())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/**").permitAll()
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/menu/**").permitAll()
                        .requestMatchers("/api/orders/**").permitAll()
                        .requestMatchers("/api/categories/**").permitAll()
                        .requestMatchers("/ws-orders/**").permitAll()
                        .requestMatchers("/api/websocket/**").permitAll()
                        .requestMatchers("/api/test/**").permitAll()
                        .requestMatchers("/actuator/**").permitAll() // Add health check endpoint
                        // Voice AI endpoints
                        .requestMatchers("/api/voice/**").permitAll()
                        .requestMatchers("/api/voice/session/start").permitAll()
                        .requestMatchers("/api/voice/session/*/process").permitAll()
                        .requestMatchers("/api/voice/session/*/tts").permitAll()
                        .requestMatchers("/api/voice/session/*/order").permitAll()
                        .requestMatchers("/api/voice/session/*/finalize").permitAll()
                        .requestMatchers("/api/voice/session/*/cancel").permitAll()
                        .requestMatchers("/api/voice/session/*/status").permitAll()
                        .requestMatchers("/api/voice/rate-limit-status").permitAll()
                        .requestMatchers("/api/voice/health").permitAll()
                        .requestMatchers("/ws/**").permitAll()
                        .requestMatchers("/app/voice/realtime/**").permitAll()
                        .requestMatchers("/topic/voice/realtime/**").permitAll()
                        .requestMatchers("/user/topic/voice/realtime/**").permitAll()
                        .requestMatchers("/api/voice/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        // Use origin patterns for more flexible matching
        configuration.setAllowedOriginPatterns(List.of(
                "http://localhost:*",
                "https://localhost:*",
                "http://192.168.*.*:*",
                "https://192.168.*.*:*",
                "http://10.0.*.*:*",
                "https://10.0.*.*:*",
                "http://172.28.42.142:*",
                "https://172.28.42.142:*",
                "https://*.ngrok-free.app",
                "https://*.ngrok.io"
        ));

        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(Arrays.asList("*"));

        // Set to false for broader mobile compatibility
        configuration.setAllowCredentials(false);

        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}