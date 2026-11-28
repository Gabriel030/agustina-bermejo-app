package com.red_escuela.backend.evaluation.exception;

import java.time.LocalDateTime;

import com.red_escuela.backend.shared.exception.ConflictException;

public class StudentExamBoardOverlapException extends ConflictException {
    public StudentExamBoardOverlapException(Long studentId, LocalDateTime examDate) {
        super("El alumno " + studentId + " ya tiene una mesa el " + examDate);
    }
}
