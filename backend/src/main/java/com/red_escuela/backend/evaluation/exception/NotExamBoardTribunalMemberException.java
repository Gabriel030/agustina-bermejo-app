package com.red_escuela.backend.evaluation.exception;

import com.red_escuela.backend.shared.exception.ForbiddenException;

public class NotExamBoardTribunalMemberException extends ForbiddenException {
    public NotExamBoardTribunalMemberException(Long instructorId, Long examBoardId) {
        super("El docente " + instructorId + " no integra el tribunal de la mesa " + examBoardId);
    }
}
