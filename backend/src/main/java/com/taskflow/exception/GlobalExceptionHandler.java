package com.taskflow.exception;

import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.taskflow.ai.exception.AiServiceException;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Centralised error handling. Every error is rendered as an RFC 9457 {@link ProblemDetail}
 * so clients only need to understand one error shape.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(TaskNotFoundException.class)
    public ProblemDetail handleTaskNotFound(TaskNotFoundException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        problem.setTitle("Task not found");
        return problem;
    }


    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
                                                                  HttpHeaders headers,
                                                                  HttpStatusCode status,
                                                                  WebRequest request) {
        Map<String, String> errors = new LinkedHashMap<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            errors.putIfAbsent(fieldError.getField(), fieldError.getDefaultMessage());
        }
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Validation failed");
        problem.setTitle("Validation error");
        problem.setProperty("errors", errors);
        return ResponseEntity.badRequest().body(problem);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException ex,
                                                                  HttpHeaders headers,
                                                                  HttpStatusCode status,
                                                                  WebRequest request) {
        String detail = "Malformed request body";
        if (ex.getCause() instanceof InvalidFormatException invalidFormat
                && invalidFormat.getTargetType() != null
                && invalidFormat.getTargetType().isEnum()) {
            String field = invalidFormat.getPath().isEmpty()
                    ? "value"
                    : invalidFormat.getPath().get(invalidFormat.getPath().size() - 1).getFieldName();
            detail = "Invalid value '" + invalidFormat.getValue() + "' for field '" + field
                    + "'. Allowed values: " + Arrays.toString(invalidFormat.getTargetType().getEnumConstants());
        }
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail);
        problem.setTitle("Bad request");
        return ResponseEntity.badRequest().body(problem);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ProblemDetail handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        String detail = "Invalid value '" + ex.getValue() + "' for parameter '" + ex.getName() + "'";
        Class<?> requiredType = ex.getRequiredType();
        if (requiredType != null && requiredType.isEnum()) {
            detail += ". Allowed values: " + Arrays.toString(requiredType.getEnumConstants());
        }
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail);
        problem.setTitle("Bad request");
        return problem;
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ProblemDetail handleConstraintViolation(ConstraintViolationException ex) {
        Map<String, String> errors = new LinkedHashMap<>();
        ex.getConstraintViolations().forEach(violation ->
                errors.putIfAbsent(violation.getPropertyPath().toString(), violation.getMessage()));
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Validation failed");
        problem.setTitle("Validation error");
        problem.setProperty("errors", errors);
        return problem;
    }

    @ExceptionHandler(AiServiceException.class)
    public ProblemDetail handleAiService(AiServiceException ex) {
        // The cause has already been logged by AiChatController with the conversation id.
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(ex.status(), ex.getMessage());
        problem.setTitle("AI service error");
        problem.setProperty("retryable", ex.retryable());
        return problem;
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception ex) {
        log.error("Unhandled exception", ex);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred");
        problem.setTitle("Internal server error");
        return problem;
    }
}
