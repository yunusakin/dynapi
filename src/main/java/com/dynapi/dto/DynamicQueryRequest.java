package com.dynapi.dto;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.util.List;

@Schema(
    name = "DynamicQueryRequest",
    description =
        "Query request for dynamic entity records with guardrailed filtering, pagination, and"
            + " sorting.")
public record DynamicQueryRequest(
    @ArraySchema(
            arraySchema = @Schema(description = "Filter tree. Empty or null means no filters."),
            schema = @Schema(implementation = FilterRule.class))
        List<FilterRule> filters,
    @Schema(description = "Zero-based page index.", example = "0", defaultValue = "0")
        @PositiveOrZero(message = "page must be greater than or equal to 0")
        Integer page,
    @Schema(description = "Page size.", example = "10", defaultValue = "10")
        @Positive(message = "size must be greater than 0")
        Integer size,
    @Schema(description = "Sortable field path from published schema.", example = "profile.age")
        String sortBy,
    @Schema(
            description = "Sort direction.",
            example = "ASC",
            allowableValues = {"ASC", "DESC"})
        @Pattern(regexp = "(?i)ASC|DESC", message = "sortDirection must be ASC or DESC")
        String sortDirection) {}
