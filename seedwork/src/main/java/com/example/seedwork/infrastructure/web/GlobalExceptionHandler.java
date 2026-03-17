package com.example.seedwork.infrastructure.web;

import com.example.seedwork.domain.DomainException;
import com.example.seedwork.domain.NotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Seedwork-level exception handler — registers at lowest precedence so that
 * service-specific {@code @RestControllerAdvice} beans always take priority.
 *
 * <p>Covers:
 * <ul>
 *   <li>{@link NotFoundException} → 404
 *   <li>{@link DomainException} (any other subtype) → 422 Unprocessable Entity
 *   <li>Spring MVC binding / validation errors → 400
 *   <li>Route and method errors → 404 / 405 / 415 / 406
 *   <li>Unhandled exceptions → 500 (message hidden from client)
 * </ul>
 *
 * <p>Response body:
 * <pre>{@code
 * {
 *   "status": 422,
 *   "message": "Insufficient stock for book: ...",
 *   "path": "/api/v1/books/123/stock/reserve",
 *   "timestamp": "2026-03-11T10:00:00Z"
 * }
 * }</pre>
 *
 * Validation errors additionally include {@code "fieldErrors": { "field": "message" }}.
 */
@RestControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE)
class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // ── Domain exceptions ────────────────────────────────────────────────────

    @ExceptionHandler(NotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    Map<String, Object> handleNotFound(NotFoundException ex, HttpServletRequest req) {
        log.warn("Not found [{}]: {}", req.getRequestURI(), ex.getMessage());
        return body(HttpStatus.NOT_FOUND, ex.getMessage(), req);
    }

    /**
     * Catches any {@link DomainException} not handled by a more specific handler.
     * Maps to 422 Unprocessable Entity: the request was well-formed, but the domain
     * refused the operation (invariant violated, business rule not met, etc.).
     */
    @ExceptionHandler(DomainException.class)
    @ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
    Map<String, Object> handleDomain(DomainException ex, HttpServletRequest req) {
        log.warn("Domain rule violation [{}]: {}", req.getRequestURI(), ex.getMessage());
        return body(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage(), req);
    }

    // ── Request binding & validation ─────────────────────────────────────────

    /**
     * {@code @Valid} / {@code @Validated} on {@code @RequestBody} — includes per-field errors.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    Map<String, Object> handleArgumentNotValid(MethodArgumentNotValidException ex,
                                               HttpServletRequest req) {
        Map<String, String> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(
                        FieldError::getField,
                        e -> e.getDefaultMessage() != null ? e.getDefaultMessage() : "invalid value",
                        (first, __) -> first));
        log.debug("Validation failed [{}]: {}", req.getRequestURI(), fieldErrors);
        return validationBody(fieldErrors, req);
    }

    /**
     * {@code @Validated} on controller class — validates {@code @PathVariable} /
     * {@code @RequestParam} via Bean Validation.
     */
    @ExceptionHandler(ConstraintViolationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    Map<String, Object> handleConstraintViolation(ConstraintViolationException ex,
                                                   HttpServletRequest req) {
        Map<String, String> fieldErrors = ex.getConstraintViolations().stream()
                .collect(Collectors.toMap(
                        v -> leafName(v.getPropertyPath().toString()),
                        ConstraintViolation::getMessage,
                        (first, __) -> first));
        log.debug("Constraint violation [{}]: {}", req.getRequestURI(), fieldErrors);
        return validationBody(fieldErrors, req);
    }

    /** Malformed JSON body or missing required body. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    Map<String, Object> handleUnreadable(HttpMessageNotReadableException ex,
                                          HttpServletRequest req) {
        log.debug("Unreadable request body [{}]: {}", req.getRequestURI(), ex.getMessage());
        return body(HttpStatus.BAD_REQUEST, "Malformed or missing request body", req);
    }

    /** Required {@code @RequestParam} is absent. */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    Map<String, Object> handleMissingParam(MissingServletRequestParameterException ex,
                                            HttpServletRequest req) {
        log.debug("Missing parameter [{}]: {}", req.getRequestURI(), ex.getMessage());
        return body(HttpStatus.BAD_REQUEST,
                "Required parameter '" + ex.getParameterName() + "' is missing", req);
    }

    /**
     * Type conversion failure for {@code @PathVariable} or {@code @RequestParam}
     * (e.g., "abc" where a UUID is expected).
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    Map<String, Object> handleTypeMismatch(MethodArgumentTypeMismatchException ex,
                                            HttpServletRequest req) {
        String expected = ex.getRequiredType() != null ? ex.getRequiredType().getSimpleName() : "unknown";
        String message = "Parameter '" + ex.getName() + "' must be of type " + expected;
        log.debug("Type mismatch [{}]: {}", req.getRequestURI(), message);
        return body(HttpStatus.BAD_REQUEST, message, req);
    }

    // ── Routing ───────────────────────────────────────────────────────────────

    /** No static resource or handler mapping found for the requested path. */
    @ExceptionHandler(NoResourceFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    Map<String, Object> handleNoResource(NoResourceFoundException ex, HttpServletRequest req) {
        log.debug("No resource [{}]", req.getRequestURI());
        return body(HttpStatus.NOT_FOUND, "No resource found at " + req.getRequestURI(), req);
    }

    /** HTTP method not supported by the matched handler. */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    @ResponseStatus(HttpStatus.METHOD_NOT_ALLOWED)
    Map<String, Object> handleMethodNotAllowed(HttpRequestMethodNotSupportedException ex,
                                                HttpServletRequest req) {
        log.debug("Method not allowed [{}] {}", ex.getMethod(), req.getRequestURI());
        return body(HttpStatus.METHOD_NOT_ALLOWED,
                ex.getMethod() + " not supported on " + req.getRequestURI(), req);
    }

    /** Client sent an unsupported Content-Type (e.g. XML to a JSON-only endpoint). */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    @ResponseStatus(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
    Map<String, Object> handleMediaTypeNotSupported(HttpMediaTypeNotSupportedException ex,
                                                     HttpServletRequest req) {
        log.debug("Unsupported media type [{}]: {}", req.getRequestURI(), ex.getContentType());
        return body(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                "Content-Type '" + ex.getContentType() + "' is not supported", req);
    }

    /** Client's Accept header cannot be satisfied by any available representation. */
    @ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
    @ResponseStatus(HttpStatus.NOT_ACCEPTABLE)
    Map<String, Object> handleMediaTypeNotAcceptable(HttpMediaTypeNotAcceptableException ex,
                                                      HttpServletRequest req) {
        log.debug("Not acceptable [{}]: {}", req.getRequestURI(), ex.getMessage());
        return body(HttpStatus.NOT_ACCEPTABLE, "No acceptable representation available", req);
    }

    // ── Fallback ─────────────────────────────────────────────────────────────

    /** Catch-all: internal details are hidden from the client to avoid information leakage. */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    Map<String, Object> handleUnexpected(Exception ex, HttpServletRequest req) {
        log.error("Unexpected error [{}]: {}", req.getRequestURI(), ex.getMessage(), ex);
        return body(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred", req);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static Map<String, Object> body(HttpStatus status, String message,
                                             HttpServletRequest req) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("status", status.value());
        map.put("message", message);
        map.put("path", req.getRequestURI());
        map.put("timestamp", Instant.now().toString());
        return map;
    }

    private static Map<String, Object> validationBody(Map<String, String> fieldErrors,
                                                       HttpServletRequest req) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("status", HttpStatus.BAD_REQUEST.value());
        map.put("message", "Validation failed");
        map.put("fieldErrors", fieldErrors);
        map.put("path", req.getRequestURI());
        map.put("timestamp", Instant.now().toString());
        return map;
    }

    /** Extracts the leaf name from a Bean Validation property path (e.g. "method.param" → "param"). */
    private static String leafName(String propertyPath) {
        int dot = propertyPath.lastIndexOf('.');
        return dot >= 0 ? propertyPath.substring(dot + 1) : propertyPath;
    }
}
