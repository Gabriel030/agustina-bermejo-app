package com.red_escuela.backend.academicstructure.exception;

import com.red_escuela.backend.shared.exception.ConflictException;

import java.time.DayOfWeek;
import java.time.LocalTime;

public class SectionScheduleConflictException extends ConflictException {
    public SectionScheduleConflictException(Long classroomId, DayOfWeek day, LocalTime start, LocalTime end) {
        super("Conflicto de horario en aula " + classroomId + ": " + day + " " + start + "-" + end);
    }
}
