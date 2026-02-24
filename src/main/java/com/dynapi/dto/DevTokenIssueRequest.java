package com.dynapi.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import java.util.List;

@Schema(
    name = "DevTokenIssueRequest",
    description = "Dev-only request to issue a JWT for local testing.")
public record DevTokenIssueRequest(
    @Schema(example = "local-admin", description = "Subject/username in JWT.") String subject,
    @Schema(example = "[\"ADMIN\",\"USER\"]", description = "Role names. ROLE_ prefix is optional.")
        List<String> roles,
    @Min(1) @Schema(example = "3600", description = "Token TTL in seconds.") Long ttlSeconds) {}
