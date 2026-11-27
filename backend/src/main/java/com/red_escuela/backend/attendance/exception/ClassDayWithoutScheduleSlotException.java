package com.red_escuela.backend.attendance.exception;

import com.red_escuela.backend.shared.exception.BadRequestException;

import java.time.LocalDate;

public class ClassDayWithoutScheduleSlotException extends BadRequestException {
    public ClassDayWithoutScheduleSlotException(LocalDate date) {
        super("No se puede tomar asistencia el " + date + ": la comisión no tiene franja horaria ese día de la semana");
    }
}
