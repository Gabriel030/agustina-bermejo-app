package com.red_escuela.backend.evaluation.exception;

import com.red_escuela.backend.shared.exception.ConflictException;

public class ClosureSnapshotNotRecalculableException extends ConflictException {
    public ClosureSnapshotNotRecalculableException(Long closureId) {
        super("El cierre " + closureId + " no admite recalcular la foto: solo se recalcula en borrador o reabierto");
    }
}
