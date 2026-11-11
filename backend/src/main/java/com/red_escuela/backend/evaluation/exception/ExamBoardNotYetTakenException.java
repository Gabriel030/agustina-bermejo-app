package com.red_escuela.backend.evaluation.exception;

import com.red_escuela.backend.shared.exception.ConflictException;
import com.red_escuela.backend.shared.exception.ReportsErrorDetails;

import java.time.LocalDateTime;
import java.util.Map;

public class ExamBoardNotYetTakenException extends ConflictException implements ReportsErrorDetails {
    private final LocalDateTime examDate;

    public ExamBoardNotYetTakenException(Long examBoardId) {
        super("La mesa " + examBoardId + " todavía no se tomó: el acta se cierra después del examen");
        this.examDate = null;
    }

    public ExamBoardNotYetTakenException(Long examBoardId, LocalDateTime examDate) {
        super("La mesa " + examBoardId + " todavía no se tomó: el acta se cierra después del examen");
        this.examDate = examDate;
    }

    @Override
    public Map<String, Object> details() {
        return examDate == null ? Map.of() : Map.of("examDate", examDate.toString());
    }
}
