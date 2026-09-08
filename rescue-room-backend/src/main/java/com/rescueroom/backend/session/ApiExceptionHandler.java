package com.rescueroom.backend.session;

import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<ProblemDetail> sessionError(ResponseStatusException error) {
        return ResponseEntity.status(error.getStatusCode())
                .body(ProblemDetail.forStatusAndDetail(error.getStatusCode(), error.getReason()));
    }
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ProblemDetail> validationError(MethodArgumentNotValidException error) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Invalid request fields");
        Map<String, String> fields = new LinkedHashMap<>();
        error.getBindingResult().getFieldErrors().forEach(field -> fields.putIfAbsent(field.getField(), field.getDefaultMessage()));
        problem.setProperty("errors", fields);
        return ResponseEntity.badRequest().body(problem);
    }
    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ProblemDetail> invalidJson() {
        return ResponseEntity.badRequest().body(ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                "A valid JSON body with correctly typed fields is required"));
    }
    @ExceptionHandler(ConcurrencyFailureException.class)
    ResponseEntity<ProblemDetail> concurrentWrite() {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
                "Session is being updated; fetch its current state before retrying"));
    }
}
