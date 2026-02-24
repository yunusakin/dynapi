package com.dynapi.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Map;

@Schema(
    name = "FormSubmissionRequest",
    description =
        "Dynamic form payload. Client selects `group` and sends arbitrary `data` that is validated"
            + " by published schema.")
public record FormSubmissionRequest(
    @Schema(
            description = "Logical form group key. Must match an existing schema group.",
            example = "profile")
        @NotBlank(message = "group must not be blank")
        String group,
    @Schema(
            description = "Dynamic key-value payload to validate and persist.",
            example = "{\"name\":\"Alice\",\"age\":30}")
        @NotNull(message = "data must not be null")
        Map<String, Object> data) {}
