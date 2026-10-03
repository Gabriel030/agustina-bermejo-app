package com.red_escuela.backend.exception;

import com.red_escuela.backend.identity.exception.InvalidEmailVerificationTokenException;
import com.red_escuela.backend.shared.exception.BadRequestException;
import com.red_escuela.backend.shared.exception.ConflictException;
import com.red_escuela.backend.shared.exception.ExternalServiceException;
import com.red_escuela.backend.shared.exception.ForbiddenException;
import com.red_escuela.backend.shared.exception.NotFoundException;
import com.red_escuela.backend.shared.exception.TooManyRequestsException;
import com.red_escuela.backend.shared.exception.UnauthorizedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
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
    public void handleMalformedBody() {
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
        return ErrorCodeResponse.of(exception);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorCodeResponse handleIllegalArgument(IllegalArgumentException exception) {
        log.warn("Argumento inválido [{}]: {}", correlation(), exception.getMessage(), exception);
        return ErrorCodeResponse.of(exception);
    }

    @ExceptionHandler(NotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public void handleNotFound() {
    }

    @ExceptionHandler(ConflictException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorCodeResponse handleConflict(ConflictException exception) {
        ErrorCodeResponse response = ErrorCodeResponse.of(exception);
        log.warn("Conflicto {} [{}]: {}", response.code(), correlation(), exception.getMessage());
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
