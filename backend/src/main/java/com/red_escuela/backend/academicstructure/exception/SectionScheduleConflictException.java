package com.red_escuela.backend.academicstructure.exception;

import java.time.DayOfWeek;
import java.time.LocalTime;

public class SectionScheduleConflictException extends RuntimeException {
    public SectionScheduleConflictException(Long classroomId, DayOfWeek day, LocalTime start, LocalTime end) {
        super("Conflicto de horario en aula " + classroomId + ": " + day + " " + start + "-" + end);
    }
}
