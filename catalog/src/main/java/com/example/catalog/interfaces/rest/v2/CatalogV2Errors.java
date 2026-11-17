package com.example.catalog.interfaces.rest.v2;

import com.example.catalog.application.BookNotFoundException;
import com.example.catalog.application.CatalogDataException;
import com.example.catalog.domain.model.CheckoutReservation;
import com.example.catalog.domain.model.VersionedBook;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@Slf4j
@Profile("demo-integrated")
@RestControllerAdvice(basePackageClasses = BookV2Controller.class)
class CatalogV2Errors {

    @ExceptionHandler(BookNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    ErrorResponse notFound() {
        return new ErrorResponse("BOOK_NOT_FOUND");
    }

    @ExceptionHandler({VersionedBook.VersionConflict.class, OptimisticLockingFailureException.class})
    @ResponseStatus(HttpStatus.CONFLICT)
    ErrorResponse versionConflict() {
        return new ErrorResponse("VERSION_CONFLICT");
    }

    @ExceptionHandler(CheckoutReservation.PayloadConflict.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    ErrorResponse reservationConflict() {
        return new ErrorResponse("RESERVATION_CONFLICT");
    }

    @ExceptionHandler(AdminWritesClosed.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    ErrorResponse adminWritesClosed() {
        return new ErrorResponse("ADMIN_WRITES_CLOSED");
    }

    @ExceptionHandler({IllegalArgumentException.class, HttpMessageNotReadableException.class,
            MethodArgumentNotValidException.class, MethodArgumentTypeMismatchException.class,
            HandlerMethodValidationException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    ErrorResponse invalidRequest() {
        return new ErrorResponse("INVALID_REQUEST");
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    @ResponseStatus(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
    ErrorResponse invalidMediaType() {
        return new ErrorResponse("INVALID_REQUEST");
    }

    @ExceptionHandler(CatalogDataException.class)
    @ResponseStatus(HttpStatus.BAD_GATEWAY)
    ErrorResponse catalogDataInvalid(CatalogDataException failure) {
        log.warn(failure.getMessage());
        return new ErrorResponse("CATALOG_DATA_INVALID");
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    ErrorResponse unexpected(Exception failure) {
        log.error("Catalog v2 request failed", failure);
        return new ErrorResponse("INTERNAL_ERROR");
    }

    record ErrorResponse(String code) {}

    static final class AdminWritesClosed extends RuntimeException {}
}
