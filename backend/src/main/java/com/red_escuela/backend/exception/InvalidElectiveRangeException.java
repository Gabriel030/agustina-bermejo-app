package com.red_escuela.backend.exception;

public class InvalidElectiveRangeException extends RuntimeException {
    public InvalidElectiveRangeException(Integer minRequired, Integer maxAllowed) {
        super("Rango de grupo electivo inválido: minRequired=" + minRequired + " > maxAllowed=" + maxAllowed);
    }
}
