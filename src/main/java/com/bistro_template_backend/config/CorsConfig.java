// src/main/java/com/bistro_template_backend/config/CorsConfig.java
package com.bistro_template_backend.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class CorsConfig implements WebMvcConfigurer {
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOriginPatterns(
                        "http://localhost:*",
                        "https://localhost:*",
                        "http://192.168.*.*:*",
                        "https://192.168.*.*:*",
                        "http://10.0.*.*:*",
                        "https://10.0.*.*:*",
                        "https://darling-treefrog-settled.ngrok-free.app",
                        "https://*.ngrok-free.app",
                        "https://*.ngrok.io"
                )
                .allowedMethods("*")
                .allowedHeaders("*")
                .allowCredentials(false); // Changed to false for broader compatibility
    }
}