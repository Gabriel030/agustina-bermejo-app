package com.red_escuela.backend.shared.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.ErrorResponse;
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
    public ErrorCodeResponse handleUnauthorized(UnauthorizedException exception) {
        return ErrorCodeResponse.of(exception);
    }

    @ExceptionHandler(InvalidEmailVerificationTokenException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorCodeResponse handleInvalidEmailVerificationToken(InvalidEmailVerificationTokenException exception) {
        return ErrorCodeResponse.of(exception);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorCodeResponse handleValidation() {
        return new ErrorCodeResponse(ErrorCodeResponse.VALIDATION_FAILED_CODE);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorCodeResponse handleMalformedBody(HttpMessageNotReadableException exception) {
        for (Throwable cause = exception.getCause(); cause != null; cause = cause.getCause() == cause ? null : cause.getCause()) {
            if (cause instanceof BadRequestException) {
                return ErrorCodeResponse.of(cause);
            }
        }
        return new ErrorCodeResponse(ErrorCodeResponse.MALFORMED_REQUEST_BODY_CODE);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorCodeResponse handlePathVariableTypeMismatch() {
        return new ErrorCodeResponse(ErrorCodeResponse.INVALID_PARAMETER_TYPE_CODE);
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
    public ResponseEntity<ErrorCodeResponse> handleNotFound(NotFoundException exception) {
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
    public ErrorCodeResponse handleForbidden(ForbiddenException exception) {
        return ErrorCodeResponse.of(exception);
    }

    @ExceptionHandler(TooManyRequestsException.class)
    @ResponseStatus(HttpStatus.TOO_MANY_REQUESTS)
    public ResponseEntity<ErrorCodeResponse> handleTooManyRequests(TooManyRequestsException exception) {
        long retryAfterSeconds = Math.max(1, exception.retryAfter().toSeconds());
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfterSeconds))
                .body(ErrorCodeResponse.of(exception));
    }

    @ExceptionHandler(CircuitOpenException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public ResponseEntity<ErrorCodeResponse> handleCircuitOpen(CircuitOpenException exception) {
        long retryAfterSeconds = Math.max(1, exception.retryAfter().toSeconds());
        log.warn("Servicio externo no disponible {} [{}]: {}", ErrorCodeResponse.codeOf(exception.getClass()), correlation(), exception.getMessage());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfterSeconds))
                .body(ErrorCodeResponse.of(exception));
    }

    @ExceptionHandler(ExternalServiceException.class)
    @ResponseStatus(HttpStatus.BAD_GATEWAY)
    public ErrorCodeResponse handleExternalService(ExternalServiceException exception) {
        log.error("Falla de servicio externo {} [{}]: {}", ErrorCodeResponse.codeOf(exception.getClass()), correlation(),
                exception.getMessage(), exception);
        return ErrorCodeResponse.of(exception);
    }

    @ExceptionHandler(DataIntegrityBrokenException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ErrorCodeResponse handleDataIntegrityBroken(DataIntegrityBrokenException exception) {
        log.error("Integridad de datos rota {} [{}]: {}", ErrorCodeResponse.codeOf(exception.getClass()), correlation(),
                exception.getMessage(), exception);
        return new ErrorCodeResponse(ErrorCodeResponse.INTERNAL_ERROR_CODE);
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
    public ErrorCodeResponse handleDataIntegrityViolation(DataIntegrityViolationException exception) {
        log.warn("Violación de integridad de datos [{}]: {}", correlation(),
                exception.getMostSpecificCause().getMessage(), exception);
        return new ErrorCodeResponse(ErrorCodeResponse.DATA_INTEGRITY_VIOLATION_CODE);
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorCodeResponse handleOptimisticLockingFailure(ObjectOptimisticLockingFailureException exception) {
        log.warn("Conflicto de concurrencia (optimistic lock) [{}]: {}", correlation(), exception.getMessage(),
                exception);
        return new ErrorCodeResponse(ErrorCodeResponse.CONCURRENT_MODIFICATION_CODE);
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ErrorCodeResponse handleUnexpected(Exception exception) throws Exception {
        if (exception instanceof AccessDeniedException
                || exception instanceof AuthenticationException
                || exception instanceof ErrorResponse
                || AnnotatedElementUtils.hasAnnotation(exception.getClass(), ResponseStatus.class)) {
            throw exception;
        }
        log.error("Error inesperado [{}]: {}", correlation(), exception.getMessage(), exception);
        return new ErrorCodeResponse(ErrorCodeResponse.INTERNAL_ERROR_CODE);
    }

    private static String correlation() {
        return "requestId=" + MDC.get(REQUEST_ID_KEY) + " userId=" + MDC.get(USER_ID_KEY);
    }
}
