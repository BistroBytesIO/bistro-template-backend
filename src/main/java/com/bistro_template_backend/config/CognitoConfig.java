package com.bistro_template_backend.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "aws.cognito")
public class CognitoConfig {
    private String userPoolId;
    private String clientId;
    private String region;

    public String getJwkUrl() {
        return String.format("https://cognito-idp.%s.amazonaws.com/%s/.well-known/jwks.json", region, userPoolId);
    }

    public String getIssuer() {
        return String.format("https://cognito-idp.%s.amazonaws.com/%s", region, userPoolId);
    }
}