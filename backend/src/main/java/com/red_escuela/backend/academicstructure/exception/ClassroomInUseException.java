package com.red_escuela.backend.academicstructure.exception;

import com.red_escuela.backend.shared.exception.ConflictException;

import java.util.List;

public class ClassroomInUseException extends ConflictException {
    public ClassroomInUseException(Long classroomId, List<String> sectionNames) {
        super("No se puede desactivar el aula " + classroomId + " porque la usan estas comisiones: " + String.join(", ", sectionNames));
    }
}
