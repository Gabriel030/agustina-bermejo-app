package com.red_escuela.backend.graduation.certificate.approvedsubjects.exception;

import com.red_escuela.backend.shared.exception.ReportsErrorCode;
import com.red_escuela.backend.shared.exception.ReportsErrorDetails;
import com.red_escuela.backend.shared.exception.UnprocessableEntityException;

import java.util.Map;

public class ApprovedSubjectsGradeUnavailableException extends UnprocessableEntityException
        implements ReportsErrorCode, ReportsErrorDetails {

    private final String subjectName;

    public ApprovedSubjectsGradeUnavailableException(String subjectName) {
        super("La nota de la materia " + subjectName + " no se puede informar en la constancia");
        this.subjectName = subjectName;
    }

    @Override
    public Map<String, Object> details() {
        return subjectName == null ? Map.of() : Map.of("subjectName", subjectName);
    }
}
