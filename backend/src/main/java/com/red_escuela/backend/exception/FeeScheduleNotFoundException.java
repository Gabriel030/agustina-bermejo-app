package com.red_escuela.backend.exception;

public class FeeScheduleNotFoundException extends RuntimeException {
    public FeeScheduleNotFoundException(String detail) {
        super("No hay un FeeSchedule vigente: " + detail);
    }
}
