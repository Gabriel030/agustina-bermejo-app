package com.red_escuela.backend.academicstructure.exception;

import com.red_escuela.backend.shared.exception.ConflictException;

import com.red_escuela.backend.academicstructure.entity.AcademicOfferingCycleStatus;

public class CycleNotEditableException extends ConflictException {
    public CycleNotEditableException(Long id, AcademicOfferingCycleStatus status) {
        super("El ciclo " + id + " está en estado " + status + " y ya no se puede modificar ni reenviar");
    }
}
