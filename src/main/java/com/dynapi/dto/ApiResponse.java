package com.dynapi.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;

@Schema(name = "ApiResponse", description = "Standard success envelope for API responses.")
public record ApiResponse<T>(
    @Schema(example = "true") boolean success,
    @Schema(example = "Query successful") String message,
    @Schema(description = "Payload body. Type depends on endpoint.") T data,
    @Schema(description = "Optional field-level error map for validation failures.")
        Map<String, String> errors,
    @Schema(description = "Optional pagination metadata.") Metadata metadata) {
  public ApiResponse(boolean success, String message, T data) {
    this(success, message, data, null, null);
  }

  @Schema(name = "ApiResponseMetadata", description = "Pagination metadata.")
  public record Metadata(
      @Schema(example = "0") int page,
      @Schema(example = "10") int size,
      @Schema(example = "25") long totalElements,
      @Schema(example = "3") int totalPages) {}

  public static <T> ApiResponse<T> success(T data) {
    return new ApiResponse<>(true, null, data, null, null);
  }

  public static <T> ApiResponse<T> success(T data, String message) {
    return new ApiResponse<>(true, message, data, null, null);
  }

  public static <T> ApiResponse<T> success(T data, Metadata metadata) {
    return new ApiResponse<>(true, null, data, null, metadata);
  }

  public static <T> ApiResponse<T> error(String message) {
    return new ApiResponse<>(false, message, null, null, null);
  }

  public static <T> ApiResponse<T> error(String message, Map<String, String> errors) {
    return new ApiResponse<>(false, message, null, errors, null);
  }
}
