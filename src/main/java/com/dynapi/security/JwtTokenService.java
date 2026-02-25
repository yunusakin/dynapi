package com.dynapi.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import javax.crypto.SecretKey;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.stereotype.Service;

@Service
public class JwtTokenService {
    private final SecretKey signingKey;

    public JwtTokenService(JwtProperties jwtProperties) {
        byte[] secretBytes = Decoders.BASE64.decode(jwtProperties.getSecret());
        this.signingKey = Keys.hmacShaKeyFor(secretBytes);
    }

    public Optional<Authentication> parseAuthentication(String token) {
        try {
            Claims claims =
                    Jwts.parser().verifyWith(signingKey).build().parseSignedClaims(token).getPayload();

            String username = claims.getSubject();
            if (username == null || username.isBlank()) {
                return Optional.empty();
            }

            Set<SimpleGrantedAuthority> authorities = extractAuthorities(claims.get("roles"));
            User principal = new User(username, "", authorities);
            Authentication authentication =
                    new UsernamePasswordAuthenticationToken(principal, token, authorities);
            return Optional.of(authentication);
        } catch (JwtException | IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    public IssuedToken issueToken(String subject, Collection<String> roles, Duration ttl) {
        if (subject == null || subject.isBlank()) {
            throw new IllegalArgumentException("Subject is required");
        }
        if (ttl == null || ttl.isNegative() || ttl.isZero()) {
            throw new IllegalArgumentException("Token ttl must be > 0");
        }

        List<String> normalizedRoles = normalizeClaimRoles(roles);
        if (normalizedRoles.isEmpty()) {
            throw new IllegalArgumentException("At least one role is required");
        }

        Instant issuedAt = Instant.now();
        Instant expiresAt = issuedAt.plus(ttl);
        String normalizedSubject = subject.trim();

        String token =
                Jwts.builder()
                        .subject(normalizedSubject)
                        .claim("roles", normalizedRoles)
                        .issuedAt(Date.from(issuedAt))
                        .expiration(Date.from(expiresAt))
                        .signWith(signingKey)
                        .compact();

        return new IssuedToken(token, issuedAt, expiresAt, normalizedSubject, normalizedRoles);
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

    private List<String> normalizeClaimRoles(Collection<String> roles) {
        if (roles == null) {
            return List.of();
        }

        Set<String> unique = new LinkedHashSet<>();
        for (String role : roles) {
            if (role == null) {
                continue;
            }
            String trimmed = role.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            if (trimmed.startsWith("ROLE_")) {
                trimmed = trimmed.substring("ROLE_".length());
            }
            if (!trimmed.isBlank()) {
                unique.add(trimmed);
            }
        }
        return new ArrayList<>(unique);
    }

    public record IssuedToken(
            String token, Instant issuedAt, Instant expiresAt, String subject, List<String> roles) {
    }
}
