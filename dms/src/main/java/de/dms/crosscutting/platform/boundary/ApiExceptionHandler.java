package de.dms.crosscutting.platform.boundary;

import de.dms.crosscutting.platform.control.ConflictException;
import de.dms.crosscutting.platform.control.ForbiddenException;
import de.dms.crosscutting.platform.control.NotFoundException;
import de.dms.crosscutting.platform.control.PayloadTooLargeException;
import de.dms.crosscutting.platform.objectstore.control.StorageUnavailableException;
import de.dms.crosscutting.platform.control.UnprocessableException;
import de.dms.crosscutting.platform.control.UnsupportedMediaTypeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.Map;

/** Maps domain exceptions to the HTTP status codes defined in §11.1. */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<Map<String, String>> notFound(NotFoundException e) {
        return body(HttpStatus.NOT_FOUND, "not found");
    }

    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<Map<String, String>> forbidden(ForbiddenException e) {
        return body(HttpStatus.FORBIDDEN, e.getMessage());
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<Map<String, String>> conflict(ConflictException e) {
        return body(HttpStatus.CONFLICT, e.getMessage());
    }

    @ExceptionHandler(UnprocessableException.class)
    public ResponseEntity<Map<String, String>> unprocessable(UnprocessableException e) {
        return body(HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage());
    }

    @ExceptionHandler(UnsupportedMediaTypeException.class)
    public ResponseEntity<Map<String, String>> unsupportedMediaType(UnsupportedMediaTypeException e) {
        return body(HttpStatus.UNSUPPORTED_MEDIA_TYPE, e.getMessage());
    }

    @ExceptionHandler({PayloadTooLargeException.class, MaxUploadSizeExceededException.class})
    public ResponseEntity<Map<String, String>> tooLarge(Exception e) {
        return body(HttpStatus.PAYLOAD_TOO_LARGE, "upload too large");
    }

    @ExceptionHandler(StorageUnavailableException.class)
    public ResponseEntity<Map<String, String>> storageUnavailable(StorageUnavailableException e) {
        LOGGER.error("object store unavailable", e);
        return body(HttpStatus.SERVICE_UNAVAILABLE, "storage unavailable");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> beanValidation(MethodArgumentNotValidException e) {
        String field = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage())
                .findFirst().orElse("validation failed");
        return body(HttpStatus.UNPROCESSABLE_ENTITY, field);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> badRequest(IllegalArgumentException e) {
        return body(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    private ResponseEntity<Map<String, String>> body(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(Map.of("error", message == null ? status.getReasonPhrase() : message));
    }
}
