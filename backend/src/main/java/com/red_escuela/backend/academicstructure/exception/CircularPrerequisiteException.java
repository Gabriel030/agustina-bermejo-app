package com.red_escuela.backend.academicstructure.exception;

public class CircularPrerequisiteException extends RuntimeException {
    public CircularPrerequisiteException(Long programSubjectId, Long prerequisiteProgramSubjectId) {
        super("Agregar esa correlatividad crearía un ciclo entre " + programSubjectId
                + " y " + prerequisiteProgramSubjectId);
    }
}
