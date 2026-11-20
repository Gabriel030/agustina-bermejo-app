package com.red_escuela.backend.identity.exception;

import com.red_escuela.backend.shared.exception.ConflictException;
import com.red_escuela.backend.shared.exception.ReportsErrorCode;

public class HistoricalPersonEmailTakenException extends ConflictException implements ReportsErrorCode {
    public HistoricalPersonEmailTakenException() {
        super("El correo ya está registrado");
    }
}
