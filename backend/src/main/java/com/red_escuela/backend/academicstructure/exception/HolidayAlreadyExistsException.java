package com.red_escuela.backend.academicstructure.exception;

import com.red_escuela.backend.shared.exception.ConflictException;

import java.time.LocalDate;

public class HolidayAlreadyExistsException extends ConflictException {
    public HolidayAlreadyExistsException(LocalDate date) {
        super("Ya existe un feriado cargado el " + date);
    }
}
