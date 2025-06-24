// File: src/main/java/com/bistro_template_backend/utils/JwtFilter.java
package com.bistro_template_backend.utils;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

@Component
public class JwtFilter extends OncePerRequestFilter {

    @Autowired
    private JwtUtil jwtUtil;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        // Only process admin routes
        String requestPath = request.getRequestURI();
        if (!isAdminRoute(requestPath)) {
            filterChain.doFilter(request, response);
            return;
        }

        final String authorizationHeader = request.getHeader(HttpHeaders.AUTHORIZATION);

        if (authorizationHeader != null && authorizationHeader.startsWith("Bearer ")) {
            String token = authorizationHeader.substring(7);

            try {
                // Only process tokens that are meant for our system (HS256)
                // AWS Cognito tokens will fail here gracefully
                Claims claims = jwtUtil.extractClaims(token);

                if (claims != null && !jwtUtil.isTokenExpired(token)) {
                    String email = claims.getSubject();
                    String role = claims.get("role", String.class);

                    // Only proceed if we have a role (our tokens have roles, Cognito tokens don't have this claim)
                    if (role != null) {
                        JwtAuthenticationToken authentication = new JwtAuthenticationToken(
                                email,
                                role,
                                Collections.singletonList(new SimpleGrantedAuthority(role))
                        );
                        SecurityContextHolder.getContext().setAuthentication(authentication);
                    }
                }
            } catch (Exception e) {
                // Log only for admin routes where we expect our tokens
                if (isAdminRoute(requestPath)) {
                    System.out.println("Admin JWT authentication failed: [REDACTED]");
                }
                // Don't set authentication, let the request continue
            }
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Check if this is an admin route that should use custom JWT
     */
    private boolean isAdminRoute(String path) {
        return path.startsWith("/api/admin/") ||
                path.equals("/api/auth/login") ||
                path.equals("/api/auth/create-admin");
    }
}