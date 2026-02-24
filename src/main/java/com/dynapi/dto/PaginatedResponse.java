package com.dynapi.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(
    name = "PaginatedResponse",
    description = "Generic paginated payload for list/query operations.")
public record PaginatedResponse<T>(
    @Schema(example = "0") int page,
    @Schema(example = "10") int size,
    @Schema(example = "1") long totalElements,
    @Schema(description = "Page content.") List<T> content,
    @Schema(example = "profile.age") String sortBy,
    @Schema(example = "ASC") String sortDirection) {}
