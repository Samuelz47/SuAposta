package com.suaposta.analytics.presentation.exception;

import com.suaposta.analytics.presentation.dto.ApiErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class DashboardExceptionHandler {

    @ExceptionHandler(UnauthorizedAnalyticsIdentityException.class)
    public ResponseEntity<ApiErrorResponse> handleUnauthorized(HttpServletRequest request) {
        return response(HttpStatus.UNAUTHORIZED, "Unauthorized", request);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidRequest(HttpServletRequest request) {
        return response(HttpStatus.BAD_REQUEST, "Invalid request", request);
    }

    private static ResponseEntity<ApiErrorResponse> response(
            HttpStatus status, String message, HttpServletRequest request) {
        return ResponseEntity.status(status).body(new ApiErrorResponse(
                Instant.now(), status.value(), status.getReasonPhrase(), message, request.getRequestURI()));
    }
}
