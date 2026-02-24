package com.dynapi.exception;

import com.dynapi.domain.exception.EntityNotFoundException;
import com.dynapi.domain.exception.ValidationException;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.validation.method.ParameterErrors;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {
  private final MessageSource messageSource;

  @ExceptionHandler(ValidationException.class)
  public ResponseEntity<ProblemDetail> handleValidationException(ValidationException ex) {
    Map<String, String> errors =
        ex.getField() == null
            ? null
            : Map.of(
                ex.getField(), ex.getMessage() == null ? "Validation failed" : ex.getMessage());
    ProblemDetail problem =
        problemDetail(
            HttpStatus.BAD_REQUEST,
            ex.getMessage() == null ? "Validation failed" : ex.getMessage(),
            "Validation Error",
            errors);
    return new ResponseEntity<>(problem, HttpStatus.BAD_REQUEST);
  }

  @ExceptionHandler(EntityNotFoundException.class)
  public ResponseEntity<ProblemDetail> handleEntityNotFoundException(EntityNotFoundException ex) {
    ProblemDetail problem =
        problemDetail(HttpStatus.NOT_FOUND, ex.getMessage(), "Entity Not Found", null);
    return new ResponseEntity<>(problem, HttpStatus.NOT_FOUND);
  }

  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<ProblemDetail> handleIllegalArgumentException(IllegalArgumentException ex) {
    ProblemDetail problem =
        problemDetail(HttpStatus.BAD_REQUEST, ex.getMessage(), "Invalid Request", null);
    return new ResponseEntity<>(problem, HttpStatus.BAD_REQUEST);
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ProblemDetail> handleAllExceptions(Exception ex, Locale locale) {
    String message =
        ex.getMessage() != null
            ? ex.getMessage()
            : messageSource.getMessage("error.internal", null, "Internal server error", locale);
    ProblemDetail problem =
        problemDetail(HttpStatus.INTERNAL_SERVER_ERROR, message, "Internal Server Error", null);
    return new ResponseEntity<>(problem, HttpStatus.INTERNAL_SERVER_ERROR);
  }

  @Override
  protected ResponseEntity<Object> handleMethodArgumentNotValid(
      MethodArgumentNotValidException ex,
      org.springframework.http.HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    Map<String, String> fieldErrors =
        ex.getBindingResult().getFieldErrors().stream()
            .collect(
                Collectors.toMap(
                    FieldError::getField,
                    fieldError -> defaultMessage(fieldError.getDefaultMessage()),
                    (left, right) -> left,
                    LinkedHashMap::new));

    ProblemDetail problem =
        problemDetail(
            status,
            "Validation failed",
            "Validation Error",
            fieldErrors.isEmpty() ? null : fieldErrors);
    return new ResponseEntity<>(problem, status);
  }

  @Override
  protected ResponseEntity<Object> handleHandlerMethodValidationException(
      HandlerMethodValidationException ex,
      org.springframework.http.HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    Map<String, String> fieldErrors = new LinkedHashMap<>();

    for (ParameterValidationResult result : ex.getParameterValidationResults()) {
      String parameterName = result.getMethodParameter().getParameterName();
      if (parameterName == null || parameterName.isBlank()) {
        parameterName = "arg" + result.getMethodParameter().getParameterIndex();
      }

      if (result instanceof ParameterErrors parameterErrors && parameterErrors.hasFieldErrors()) {
        for (FieldError fieldError : parameterErrors.getFieldErrors()) {
          String key =
              fieldError.getField() == null || fieldError.getField().isBlank()
                  ? parameterName
                  : fieldError.getField();
          fieldErrors.putIfAbsent(key, defaultMessage(fieldError.getDefaultMessage()));
        }
      } else {
        for (MessageSourceResolvable resolvable : result.getResolvableErrors()) {
          fieldErrors.putIfAbsent(parameterName, defaultMessage(resolvable.getDefaultMessage()));
        }
      }
    }

    ProblemDetail problem =
        problemDetail(
            status,
            "Validation failed",
            "Validation Error",
            fieldErrors.isEmpty() ? null : fieldErrors);
    return new ResponseEntity<>(problem, status);
  }

  private ProblemDetail problemDetail(
      HttpStatusCode status, String detail, String title, Map<String, String> errors) {
    ProblemDetail problem =
        ProblemDetail.forStatusAndDetail(status, detail == null ? "Request failed" : detail);
    problem.setTitle(title);
    if (errors != null && !errors.isEmpty()) {
      problem.setProperty("errors", errors);
    }
    return problem;
  }

  private String defaultMessage(String message) {
    return message == null || message.isBlank() ? "Invalid value" : message;
  }
}
