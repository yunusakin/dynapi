package com.example.dynapi.interfaces.rest.dto;

import lombok.Data;
import java.util.Map;

@Data
public class ApiResponse<T> {
    private boolean success;
    private T data;
    private Map<String, String> errors;
    private String message;
    private Metadata metadata;

    @Data
    public static class Metadata {
        private int page;
        private int size;
        private long totalElements;
        private int totalPages;
    }

    public static <T> ApiResponse<T> success(T data) {
        ApiResponse<T> response = new ApiResponse<>();
        response.setSuccess(true);
        response.setData(data);
        return response;
    }

    public static <T> ApiResponse<T> success(T data, Metadata metadata) {
        ApiResponse<T> response = success(data);
        response.setMetadata(metadata);
        return response;
    }

    public static <T> ApiResponse<T> error(String message) {
        ApiResponse<T> response = new ApiResponse<>();
        response.setSuccess(false);
        response.setMessage(message);
        return response;
    }

    public static <T> ApiResponse<T> error(String message, Map<String, String> errors) {
        ApiResponse<T> response = error(message);
        response.setErrors(errors);
        return response;
    }
}
