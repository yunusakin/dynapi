package com.dynapi.security;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

class CurrentActorResolverTest {

    private final CurrentActorResolver currentActorResolver = new CurrentActorResolver();

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void resolve_returnsAuthenticatedPrincipalName() {
        Authentication authentication =
                new UsernamePasswordAuthenticationToken(
                        "alice", null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        SecurityContextHolder.getContext().setAuthentication(authentication);

        assertEquals("alice", currentActorResolver.resolve());
    }

    @Test
    void resolve_returnsSystemWhenNoAuthenticationPresent() {
        SecurityContextHolder.clearContext();

        assertEquals("system", currentActorResolver.resolve());
    }

    @Test
    void resolve_returnsSystemWhenAuthenticationNameIsBlank() {
        Authentication authentication = new UsernamePasswordAuthenticationToken("   ", null);
        SecurityContextHolder.getContext().setAuthentication(authentication);

        assertEquals("system", currentActorResolver.resolve());
    }
}
