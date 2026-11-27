package com.red_escuela.backend.academicstructure.exception;

import com.red_escuela.backend.shared.exception.ConflictException;

public class TermNotOpenForOfferingException extends ConflictException {
    public TermNotOpenForOfferingException(Long termId) {
        super("El término " + termId + " está inactivo o ya finalizó: no admite ciclos ni comisiones nuevas");
    }
}
