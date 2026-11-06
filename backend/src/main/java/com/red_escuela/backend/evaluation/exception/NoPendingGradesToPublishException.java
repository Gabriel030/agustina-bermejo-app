package com.red_escuela.backend.evaluation.exception;

import com.red_escuela.backend.shared.exception.ConflictException;

public class NoPendingGradesToPublishException extends ConflictException {
    public NoPendingGradesToPublishException(Long sectionId, String type, Integer sequenceNumber) {
        super("No hay notas nuevas para publicar en la comisión " + sectionId + " (" + type + " " + sequenceNumber + ")");
    }
}
