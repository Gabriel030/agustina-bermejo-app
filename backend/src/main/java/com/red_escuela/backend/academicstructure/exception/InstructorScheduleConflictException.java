package com.red_escuela.backend.academicstructure.exception;

import com.red_escuela.backend.shared.exception.ConflictException;

import java.time.DayOfWeek;
import java.time.LocalTime;

public class InstructorScheduleConflictException extends ConflictException {
    public InstructorScheduleConflictException(Long instructorId, DayOfWeek day, LocalTime start, LocalTime end) {
        super("El docente " + instructorId + " ya tiene otra comisión asignada en el horario " + day + " " + start + "-" + end);
    }
}
