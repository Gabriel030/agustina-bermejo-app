package com.red_escuela.backend.academicstructure.exception;

import com.red_escuela.backend.shared.exception.BadRequestException;

import java.time.LocalDateTime;

public class TermYearMismatchException extends BadRequestException {
    public TermYearMismatchException(int year, LocalDateTime startDate) {
        super("El año " + year + " del período no coincide con su fecha de inicio (" + startDate + ")");
    }
}
