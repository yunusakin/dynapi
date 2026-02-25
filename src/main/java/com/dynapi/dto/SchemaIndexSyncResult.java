package com.dynapi.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(
        name = "SchemaIndexSyncResult",
        description = "Summary of index synchronization against the latest published schema.")
public record SchemaIndexSyncResult(
        @Schema(example = "tasks") String entity,
        @Schema(example = "2") Integer schemaVersion,
        @Schema(example = "3") int requestedIndexes,
        @Schema(example = "3") int ensuredIndexes,
        @Schema(example = "[\"email\",\"profile.phone\"]") List<String> uniqueFields,
        @Schema(example = "[\"priority\",\"profile.city\"]") List<String> indexedFields) {
}
