package com.red_escuela.backend.academicstructure.exception;

import com.red_escuela.backend.shared.exception.BadRequestException;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.BAD_REQUEST)
public class InvalidProgramSubjectCategoryException extends BadRequestException {
    public InvalidProgramSubjectCategoryException(String message) {
        super(message);
    }
}
