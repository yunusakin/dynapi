package com.dynapi.interfaces.rest.dto;

import java.util.Map;

public class ErrorResponse {
    private String message;
    private String field;
    private Map<String, String> details;

    public ErrorResponse(String message) {
        this.message = message;
    }

    public ErrorResponse(String message, String field) {
        this.message = message;
        this.field = field;
    }

    public ErrorResponse(String message, Map<String, String> details) {
        this.message = message;
        this.details = details;
    }

    public String getMessage() {
        return message;
    }

    public String getField() {
        return field;
    }

    public Map<String, String> getDetails() {
        return details;
    }
}
