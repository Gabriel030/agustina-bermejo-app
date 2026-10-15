package com.red_escuela.backend.shared.exception;

import com.red_escuela.backend.identity.exception.InvalidEmailVerificationTokenException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String REQUEST_ID_KEY = "requestId";
    private static final String USER_ID_KEY = "userId";

    @ExceptionHandler(UnauthorizedException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public void handleUnauthorized() {
    }

    @ExceptionHandler(InvalidEmailVerificationTokenException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public void handleInvalidEmailVerificationToken() {
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public void handleValidation() {
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorCodeResponse handleMalformedBody(HttpMessageNotReadableException exception) {
        for (Throwable cause = exception.getCause(); cause != null; cause = cause.getCause() == cause ? null : cause.getCause()) {
            if (cause instanceof BadRequestException) {
                return ErrorCodeResponse.of(cause);
            }
        }
        return null;
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public void handlePathVariableTypeMismatch() {
    }

    @ExceptionHandler({
            BadRequestException.class,
            MaxUploadSizeExceededException.class
    })
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorCodeResponse handleBadRequest(Exception exception) {
        ErrorCodeResponse response = ErrorCodeResponse.of(exception);
        if (!response.violations().isEmpty()) {
            log.warn("Rechazo {} [{}]: violaciones={}", response.code(), correlation(), response.violations().stream().map(v -> v.code()).toList());
        }
        return response;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorCodeResponse handleIllegalArgument(IllegalArgumentException exception) {
        log.warn("Argumento inválido [{}]: {}", correlation(), exception.getMessage(), exception);
        return ErrorCodeResponse.of(exception);
    }

    @ExceptionHandler(NotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ResponseEntity<?> handleNotFound(NotFoundException exception) {
        return exception instanceof ReportsErrorCode
                ? ResponseEntity.status(HttpStatus.NOT_FOUND).body(ErrorCodeResponse.of(exception))
                : ResponseEntity.status(HttpStatus.NOT_FOUND).build();
    }

    @ExceptionHandler(ConflictException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorCodeResponse handleConflict(ConflictException exception) {
        ErrorCodeResponse response = ErrorCodeResponse.of(exception);
        log.warn("Conflicto {} [{}]: {} violaciones={}", response.code(), correlation(), exception.getMessage(), response.violations().stream().map(v -> v.code()).toList());
        return response;
    }

    @ExceptionHandler(ForbiddenException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public void handleForbidden() {
    }

    @ExceptionHandler(TooManyRequestsException.class)
    @ResponseStatus(HttpStatus.TOO_MANY_REQUESTS)
    public void handleTooManyRequests() {
    }

    @ExceptionHandler(ExternalServiceException.class)
    @ResponseStatus(HttpStatus.BAD_GATEWAY)
    public void handleExternalService(ExternalServiceException exception) {
        log.error("Falla de servicio externo {} [{}]: {}", ErrorCodeResponse.of(exception).code(), correlation(),
                exception.getMessage(), exception);
    }

    @ExceptionHandler(IllegalStateException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorCodeResponse handleIllegalState(IllegalStateException exception) {
        ErrorCodeResponse response = ErrorCodeResponse.of(exception);
        log.warn("Conflicto de estado {} [{}]: {}", response.code(), correlation(), exception.getMessage(), exception);
        return response;
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public void handleDataIntegrityViolation(DataIntegrityViolationException exception) {
        log.warn("Violación de integridad de datos [{}]: {}", correlation(),
                exception.getMostSpecificCause().getMessage(), exception);
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public void handleOptimisticLockingFailure(ObjectOptimisticLockingFailureException exception) {
        log.warn("Conflicto de concurrencia (optimistic lock) [{}]: {}", correlation(), exception.getMessage(),
                exception);
    }

    private static String correlation() {
        return "requestId=" + MDC.get(REQUEST_ID_KEY) + " userId=" + MDC.get(USER_ID_KEY);
    }
}
