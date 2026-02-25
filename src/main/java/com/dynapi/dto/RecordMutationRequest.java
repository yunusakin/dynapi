package com.dynapi.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.util.Map;

@Schema(
    name = "RecordMutationRequest",
    description = "Dynamic record mutation payload used by PATCH/PUT record endpoints.")
public record RecordMutationRequest(
    @Schema(
            description = "Dynamic key-value payload to validate and persist.",
            example = "{\"name\":\"Alice\",\"age\":31}")
        @NotNull(message = "data must not be null")
        Map<String, Object> data) {}
