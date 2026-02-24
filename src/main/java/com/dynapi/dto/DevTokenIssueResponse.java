package com.dynapi.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

@Schema(name = "DevTokenIssueResponse", description = "Issued JWT details for local development.")
public record DevTokenIssueResponse(
    @Schema(description = "Signed JWT access token.") String token,
    @Schema(example = "Bearer") String tokenType,
    @Schema(example = "local-admin") String subject,
    @Schema(example = "[\"ADMIN\"]") List<String> roles,
    @Schema(description = "Issued-at timestamp.") Instant issuedAt,
    @Schema(description = "Expiry timestamp.") Instant expiresAt) {}
