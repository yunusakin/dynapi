package com.dynapi.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

@Service
public class JwtTokenService {
    private final SecretKey signingKey;

    public JwtTokenService(JwtProperties jwtProperties) {
        byte[] secretBytes = Decoders.BASE64.decode(jwtProperties.getSecret());
        this.signingKey = Keys.hmacShaKeyFor(secretBytes);
    }

    public Optional<Authentication> parseAuthentication(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            String username = claims.getSubject();
            if (username == null || username.isBlank()) {
                return Optional.empty();
            }

            Set<SimpleGrantedAuthority> authorities = extractAuthorities(claims.get("roles"));
            User principal = new User(username, "", authorities);
            Authentication authentication = new UsernamePasswordAuthenticationToken(principal, token, authorities);
            return Optional.of(authentication);
        } catch (JwtException | IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    private Set<SimpleGrantedAuthority> extractAuthorities(Object rolesClaim) {
        if (rolesClaim == null) {
            return Collections.emptySet();
        }

        Set<SimpleGrantedAuthority> authorities = new HashSet<>();
        if (rolesClaim instanceof String singleRole) {
            addRole(authorities, singleRole);
            return authorities;
        }

        if (rolesClaim instanceof Collection<?> roles) {
            for (Object role : roles) {
                if (role != null) {
                    addRole(authorities, role.toString());
                }
            }
        }
        return authorities;
    }

    private void addRole(Set<SimpleGrantedAuthority> authorities, String role) {
        String trimmed = role == null ? "" : role.trim();
        if (trimmed.isEmpty()) {
            return;
        }
        String normalized = trimmed.startsWith("ROLE_") ? trimmed : "ROLE_" + trimmed;
        authorities.add(new SimpleGrantedAuthority(normalized));
    }
}
