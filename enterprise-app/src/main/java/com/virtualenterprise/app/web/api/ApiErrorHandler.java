package com.virtualenterprise.app.web.api;

import com.virtualenterprise.app.service.AppException;
import org.springframework.http.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.RestClientException;

import java.time.Instant;
import java.util.*;

@RestControllerAdvice(basePackages = "com.virtualenterprise.app.web.api")
public class ApiErrorHandler {
    @ExceptionHandler(AppException.class)
    ResponseEntity<Map<String, Object>> app(AppException ex) {
        return ResponseEntity.status(ex.getStatus()).body(error(ex.getStatus(), ex.getCode(), ex.getMessage(), ex.getDetails()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<Map<String, Object>> validation(MethodArgumentNotValidException ex) {
        List<Map<String, String>> fields = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> Map.of("field", e.getField(), "message", Objects.toString(e.getDefaultMessage(), "Некорректное значение")))
                .toList();
        return ResponseEntity.badRequest().body(error(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                "Проверьте заполнение полей", Map.of("fieldErrors", fields)));
    }

    @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
    ResponseEntity<Map<String, Object>> accessDenied(org.springframework.security.access.AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error(HttpStatus.FORBIDDEN, "ACCESS_DENIED",
                "Недостаточно прав для выполнения операции", Map.of()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<Map<String, Object>> invalidArgument(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(error(HttpStatus.BAD_REQUEST, "INVALID_ARGUMENT",
                ex.getMessage(), Map.of()));
    }

    @ExceptionHandler(RestClientException.class)
    ResponseEntity<Map<String, Object>> workload(RestClientException ex) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(error(HttpStatus.SERVICE_UNAVAILABLE,
                "WORKLOAD_SERVICE_UNAVAILABLE", "Сервис расчёта загрузки недоступен", Map.of()));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<Map<String, Object>> generic(Exception ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error(HttpStatus.INTERNAL_SERVER_ERROR,
                "INTERNAL_ERROR", "Внутренняя ошибка сервера", Map.of()));
    }

    private Map<String, Object> error(HttpStatus status, String code, String message, Map<String, Object> details) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now().toString());
        body.put("status", status.value());
        body.put("code", code);
        body.put("message", message);
        body.put("details", details);
        return body;
    }
}
