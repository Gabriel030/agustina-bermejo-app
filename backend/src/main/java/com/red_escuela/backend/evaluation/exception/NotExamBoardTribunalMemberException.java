package com.red_escuela.backend.evaluation.exception;

public class NotExamBoardTribunalMemberException extends RuntimeException {
    public NotExamBoardTribunalMemberException(Long instructorId, Long examBoardId) {
        super("El docente " + instructorId + " no integra el tribunal de la mesa " + examBoardId);
    }
}
