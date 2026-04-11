package pl.szelag.ai_knowledge_base.exception;

import java.net.URI;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import lombok.extern.slf4j.Slf4j;

/**
 * Global exception handler for REST API. Produces RFC7807 ProblemDetail
 * responses.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** Handles bean validation errors (@Valid) — returns 400 with field details. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException e) {

        String details = e.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.joining(", "));

        log.warn("Validation failed: {}", details);

        ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        pd.setTitle("Validation failed");
        // Placeholder URI per RFC 7807 §3.1 — replace with real docs URL before
        // production.
        pd.setType(URI.create("https://api.error/validation-error"));
        pd.setDetail(details);

        return pd;
    }

    /** Handles invalid arguments — returns 400 with message. */
    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleIllegalArgument(IllegalArgumentException e) {

        log.warn("Invalid argument: {}", e.getMessage());

        ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        pd.setTitle("Invalid request parameter");
        pd.setType(URI.create("https://api.error/invalid-argument"));
        pd.setDetail(e.getMessage());

        return pd;
    }

    /** Handles all unexpected errors — returns generic 500 response. */
    @ExceptionHandler(Exception.class)
    public ProblemDetail handleGeneral(Exception e) {

        log.error("Unhandled exception: {}", e.getMessage(), e);

        ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.INTERNAL_SERVER_ERROR);
        pd.setTitle("Internal server error");
        pd.setType(URI.create("https://api.error/internal-error"));
        pd.setDetail("Service unavailable. Please try again.");

        return pd;
    }
}