package com.personalos.backend.common.error;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.time.Instant;
import java.util.List;

/**
 * Turns every failure, including Spring MVC's own (404, 405, malformed JSON), into the
 * standard {@link ApiError} body.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiError> handleApi(ApiException ex, HttpServletRequest request) {
        return build(ex.getStatus(), ex.getCode(), ex.getMessage(), request.getRequestURI(), List.of());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception on {}", request.getRequestURI(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Something went wrong",
                request.getRequestURI(), List.of());
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
                                                                  HttpHeaders headers, HttpStatusCode status,
                                                                  WebRequest request) {
        List<ApiError.FieldViolation> violations = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> new ApiError.FieldViolation(e.getField(), e.getDefaultMessage()))
                .toList();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
                apiError(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Request validation failed",
                        path(request), violations));
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers,
                                                             HttpStatusCode statusCode, WebRequest request) {
        HttpStatus status = HttpStatus.valueOf(statusCode.value());
        String code = status == HttpStatus.BAD_REQUEST ? "BAD_REQUEST" : status.name();
        String message = status.is4xxClientError() ? status.getReasonPhrase() : "Something went wrong";
        return ResponseEntity.status(status).headers(headers)
                .body(apiError(status, code, message, path(request), List.of()));
    }

    private static ResponseEntity<ApiError> build(HttpStatus status, String code, String message, String path,
                                                  List<ApiError.FieldViolation> violations) {
        return ResponseEntity.status(status).body(apiError(status, code, message, path, violations));
    }

    private static ApiError apiError(HttpStatus status, String code, String message, String path,
                                     List<ApiError.FieldViolation> violations) {
        return new ApiError(Instant.now(), status.value(), code, message, path, violations);
    }

    private static String path(WebRequest request) {
        return request instanceof ServletWebRequest r ? r.getRequest().getRequestURI() : "";
    }
}
