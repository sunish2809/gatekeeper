package com.project.gatekeeper.analytics.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(TypeMismatchException.class)
    public ResponseEntity<Map<String, String>> handleBadParam(TypeMismatchException ex) {
        String name = ex.getPropertyName() == null ? "A parameter" : ex.getPropertyName();
        return ResponseEntity.badRequest().body(body("invalid_request", name + " has an invalid value"));
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<Map<String, String>> handleStore(DataAccessException ex) {
        log.error("MongoDB query failed", ex);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(body("store_unavailable", "Analytics store is unavailable"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleUnexpected(Exception ex) {
        if (ex instanceof ErrorResponse error) {
            String detail = error.getBody().getDetail();
            return ResponseEntity.status(error.getStatusCode())
                    .body(body("request_failed", detail == null ? "Request failed" : detail));
        }
        log.error("Unhandled error", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(body("internal_error", "Something went wrong. Quote X-Correlation-Id when reporting it."));
    }

    private Map<String, String> body(String error, String message) {
        return Map.of("error", error, "message", message);
    }
}
