package com.red_escuela.backend.enrollment.exception;

import java.time.LocalDate;
import com.red_escuela.backend.shared.exception.UnprocessableEntityException;

public class FutureDateException extends UnprocessableEntityException {
    public FutureDateException(LocalDate date) {
        super("La fecha de reconocimiento no puede ser futura: " + date);
    }
}
