package com.tenant.serverj.exception;

import java.util.Collections;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandlerC {

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<Map<String,String>> handleApiException(ApiException exception) {
        return ResponseEntity.status(exception.getStatus()).body(Collections.singletonMap("404", "Not found"));
    }
}