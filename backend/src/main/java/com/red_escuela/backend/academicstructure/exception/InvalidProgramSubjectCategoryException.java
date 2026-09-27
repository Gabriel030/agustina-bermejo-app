package com.red_escuela.backend.academicstructure.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.BAD_REQUEST)
public class InvalidProgramSubjectCategoryException extends RuntimeException {
    public InvalidProgramSubjectCategoryException(String message) {
        super(message);
    }
}
