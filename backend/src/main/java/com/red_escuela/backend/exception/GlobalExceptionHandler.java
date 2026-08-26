package com.red_escuela.backend.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(InvalidCredentialsException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public void handleInvalidCredentials() {
    }

    @ExceptionHandler(InvalidRefreshTokenException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public void handleInvalidRefreshToken() {
    }

    @ExceptionHandler(InvalidPasswordResetTokenException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public void handleInvalidPasswordResetToken() {
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public void handleValidation() {
    }

    @ExceptionHandler({
            UnsupportedFileTypeException.class,
            MaxUploadSizeExceededException.class,
            DniAlreadyExistsException.class,
            InvalidStaffRoleException.class
    })
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public void handleBadRequest() {
    }

    @ExceptionHandler({
            StudentNotFoundException.class,
            InstitutionNotFoundException.class,
            RoleNotFoundException.class,
            PersonNotFoundException.class
    })
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public void handleNotFound() {
    }

    @ExceptionHandler(ProspectAlreadyConfirmedException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public void handleConflict() {
    }
}
