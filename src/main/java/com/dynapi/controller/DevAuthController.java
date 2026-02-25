package com.dynapi.controller;

import com.dynapi.dto.ApiResponse;
import com.dynapi.dto.DevTokenIssueRequest;
import com.dynapi.dto.DevTokenIssueResponse;
import com.dynapi.security.DevAuthProperties;
import com.dynapi.security.JwtTokenService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping(path = "/dev/auth", version = "1")
@ConditionalOnProperty(prefix = "dynapi.dev-auth", name = "enabled", havingValue = "true")
public class DevAuthController {
    private final JwtTokenService jwtTokenService;
    private final DevAuthProperties devAuthProperties;

    @PostMapping("/token")
    @Operation(
            summary = "Issue Dev JWT",
            description =
                    "Dev-only endpoint for local token generation. Disabled unless"
                            + " dynapi.dev-auth.enabled=true.")
    public ApiResponse<DevTokenIssueResponse> issueToken(
            @RequestBody(required = false) @Valid DevTokenIssueRequest request) {
        DevTokenIssueRequest safeRequest =
                request == null ? new DevTokenIssueRequest(null, null, null) : request;

        String subject =
                StringUtils.hasText(safeRequest.subject())
                        ? safeRequest.subject().trim()
                        : devAuthProperties.getDefaultSubject();
        List<String> roles = resolveRoles(safeRequest.roles());
        long ttlSeconds =
                safeRequest.ttlSeconds() == null
                        ? devAuthProperties.getDefaultTtlSeconds()
                        : safeRequest.ttlSeconds();

        if (ttlSeconds <= 0) {
            throw new IllegalArgumentException("ttlSeconds must be > 0");
        }
        if (ttlSeconds > devAuthProperties.getMaxTtlSeconds()) {
            throw new IllegalArgumentException(
                    "ttlSeconds exceeds max allowed: " + devAuthProperties.getMaxTtlSeconds());
        }

        JwtTokenService.IssuedToken issuedToken =
                jwtTokenService.issueToken(subject, roles, Duration.ofSeconds(ttlSeconds));

        DevTokenIssueResponse response =
                new DevTokenIssueResponse(
                        issuedToken.token(),
                        "Bearer",
                        issuedToken.subject(),
                        issuedToken.roles(),
                        issuedToken.issuedAt(),
                        issuedToken.expiresAt());
        return ApiResponse.success(response, "Dev token issued");
    }

    private List<String> resolveRoles(List<String> requestedRoles) {
        List<String> roleSource =
                requestedRoles == null || requestedRoles.isEmpty()
                        ? devAuthProperties.getDefaultRoles()
                        : requestedRoles;

        Set<String> unique = new LinkedHashSet<>();
        for (String role : roleSource) {
            if (!StringUtils.hasText(role)) {
                continue;
            }
            String normalized = role.trim();
            if (normalized.startsWith("ROLE_")) {
                normalized = normalized.substring("ROLE_".length());
            }
            if (!normalized.isBlank()) {
                unique.add(normalized);
            }
        }
        if (unique.isEmpty()) {
            throw new IllegalArgumentException("At least one role is required");
        }
        return new ArrayList<>(unique);
    }
}
