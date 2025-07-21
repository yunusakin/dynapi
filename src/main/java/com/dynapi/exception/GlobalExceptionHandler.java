package com.dynapi.exception;

import com.dynapi.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;

import java.util.Locale;

@ControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler {
    private final MessageSource messageSource;

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleAllExceptions(Exception ex, WebRequest request, Locale locale) {
        String message = ex.getMessage() != null ? ex.getMessage() : messageSource.getMessage("error.internal", null, locale);
        ApiResponse<Void> response = new ApiResponse<>(false, message, null);
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidationExceptions(MethodArgumentNotValidException ex, Locale locale) {
        String message = ex.getBindingResult().getAllErrors().get(0).getDefaultMessage();
        ApiResponse<Void> response = new ApiResponse<>(false, message, null);
        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }
}
