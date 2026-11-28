package com.red_escuela.backend.academicstructure.exception;

import com.red_escuela.backend.academicstructure.domain.TermType;
import com.red_escuela.backend.academicstructure.entity.SubjectRecurrence;
import com.red_escuela.backend.shared.exception.ConflictException;

public class SubjectRecurrenceIncompatibleWithTermException extends ConflictException {
    public SubjectRecurrenceIncompatibleWithTermException(Long subjectId, SubjectRecurrence recurrence, TermType termType) {
        super("La materia " + subjectId + " con recurrencia " + recurrence + " no se puede ofrecer en un término de tipo " + termType);
    }
}
