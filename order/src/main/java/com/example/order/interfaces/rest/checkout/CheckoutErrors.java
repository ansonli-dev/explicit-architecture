package com.example.order.interfaces.rest.checkout;

import com.example.order.application.checkout.CheckoutProblem;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@Profile("demo-integrated")
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(basePackageClasses = CheckoutController.class)
class CheckoutErrors {

    @ExceptionHandler(CheckoutProblem.class)
    ResponseEntity<ErrorResponse> problem(CheckoutProblem problem) {
        var status = switch (problem.code()) {
            case INVALID_REQUEST -> HttpStatus.BAD_REQUEST;
            case EMAIL_UNVERIFIED -> HttpStatus.FORBIDDEN;
            case NOT_FOUND, BOOK_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case QUOTE_EXPIRED, QUOTE_CONSUMED, IDEMPOTENCY_CONFLICT, INSUFFICIENT_STOCK, UNSUPPORTED_CURRENCY,
                    VERSION_CONFLICT, ORDER_STATE_CONFLICT -> HttpStatus.CONFLICT;
            case INTAKE_CLOSED, CATALOG_UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE;
            case AMOUNT_OUT_OF_RANGE, CATALOG_DATA_INVALID -> HttpStatus.BAD_GATEWAY;
        };
        return ResponseEntity.status(status).body(new ErrorResponse(problem.code()));
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, MissingRequestHeaderException.class,
            HandlerMethodValidationException.class, MethodArgumentTypeMismatchException.class, HttpMessageNotReadableException.class})
    ResponseEntity<ErrorResponse> badRequest() {
        return ResponseEntity.badRequest().body(new ErrorResponse(CheckoutProblem.Code.INVALID_REQUEST));
    }

    record ErrorResponse(CheckoutProblem.Code code) {}
}
