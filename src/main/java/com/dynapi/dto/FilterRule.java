package com.dynapi.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(
        name = "FilterRule",
        description = "Single filter or combinator rule. Use `rules` for `and/or/not` combinators.")
public record FilterRule(
        @Schema(description = "Field path. Required for leaf operators.", example = "profile.age")
        String field,
        @Schema(
                description =
                        "Operator. Leaf examples: eq, ne, gt, gte, lt, lte, in, nin, regex, exists."
                                + " Combinators: and, or, not (case-insensitive: AND/OR/NOT supported).",
                example = "eq")
        String operator,
        @Schema(description = "Comparison value for leaf operators.", example = "Alice") Object value,
        @Schema(description = "Nested rules used by combinators.", nullable = true)
        List<FilterRule> rules) {
}
