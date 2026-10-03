package com.red_escuela.backend.academicstructure.exception;

import com.red_escuela.backend.shared.exception.BadRequestException;

public class CircularPrerequisiteException extends BadRequestException {
    public CircularPrerequisiteException(Long programSubjectId, Long prerequisiteProgramSubjectId) {
        super("Agregar esa correlatividad crearía un ciclo entre " + programSubjectId
                + " y " + prerequisiteProgramSubjectId);
    }
}
