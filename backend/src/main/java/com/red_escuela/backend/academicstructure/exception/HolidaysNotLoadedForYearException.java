package com.red_escuela.backend.academicstructure.exception;

import com.red_escuela.backend.shared.exception.ConflictException;

public class HolidaysNotLoadedForYearException extends ConflictException {
    public HolidaysNotLoadedForYearException(int year) {
        super("No hay feriados cargados para el año " + year);
    }
}
