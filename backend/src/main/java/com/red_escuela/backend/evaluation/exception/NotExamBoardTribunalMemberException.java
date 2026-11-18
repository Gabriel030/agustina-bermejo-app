package com.red_escuela.backend.evaluation.exception;

import com.red_escuela.backend.shared.exception.NotFoundException;

public class NotExamBoardTribunalMemberException extends NotFoundException {
    public NotExamBoardTribunalMemberException(Long instructorId, Long examBoardId) {
        super("El docente " + instructorId + " no integra el tribunal de la mesa " + examBoardId);
    }
}
