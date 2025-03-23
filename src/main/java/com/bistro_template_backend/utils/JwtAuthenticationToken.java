package com.bistro_template_backend.utils;

import lombok.Getter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import java.util.Collection;
import org.springframework.security.core.GrantedAuthority;

public class JwtAuthenticationToken extends AbstractAuthenticationToken {
    private final String email;
    @Getter
    private final String role;

    public JwtAuthenticationToken(String email, String role, Collection<? extends GrantedAuthority> authorities) {
        super(authorities);
        this.email = email;
        this.role = role;
        setAuthenticated(true);
    }

    @Override
    public Object getCredentials() {
        return null;
    }

    @Override
    public Object getPrincipal() {
        return email;
    }

}
