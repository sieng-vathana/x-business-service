package com.x.business.exception;

import com.sharedlib.response.ApiResponse;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

/** Returns the reason of a rejected request, e.g. a taken business code, in the API envelope the BFF reads. */
@RestControllerAdvice
public class BusinessExceptionHandler {

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiResponse<Void>> handleResponseStatus(ResponseStatusException exception) {
        HttpStatusCode status = exception.getStatusCode();
        String message = exception.getReason() == null
                ? "Request could not be processed" : exception.getReason();
        return ResponseEntity.status(status)
                .body(ApiResponse.error(status.value(), message));
    }
}
