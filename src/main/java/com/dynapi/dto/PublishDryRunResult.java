package com.dynapi.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(
        name = "PublishDryRunResult",
        description =
                "Preview of a schema publish: compatibility outcome and breaking/non-breaking field"
                        + " changes, without mutating any lifecycle state.")
public record PublishDryRunResult(
        @Schema(example = "tasks") String entity,
        @Schema(example = "task-form") String groupName,
        @Schema(example = "1") Integer currentVersion,
        @Schema(example = "2") Integer candidateVersion,
        @Schema(example = "true") boolean compatible,
        @Schema(description = "Changes that would block publish if applied.")
                List<String> blockingChanges,
        @Schema(description = "Changes that are safe to publish.") List<String> nonBreakingChanges) {
}
