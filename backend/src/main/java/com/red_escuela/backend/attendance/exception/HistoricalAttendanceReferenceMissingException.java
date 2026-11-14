package com.red_escuela.backend.attendance.exception;

import com.red_escuela.backend.shared.exception.ConflictException;
import com.red_escuela.backend.shared.exception.ReportsErrorCode;

public class HistoricalAttendanceReferenceMissingException extends ConflictException implements ReportsErrorCode {
    public HistoricalAttendanceReferenceMissingException(Long sectionId) {
        super("La comisión " + sectionId + " no tiene cursada importada del alumno o docente titular");
    }
}
