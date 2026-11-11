package com.red_escuela.backend.evaluation.exception;

import com.red_escuela.backend.shared.exception.ConflictException;
import com.red_escuela.backend.shared.exception.ReportsErrorDetails;

import java.util.List;
import java.util.Map;

public class FinalExamPrerequisitesNotApprovedException extends ConflictException implements ReportsErrorDetails {
    private final Long enrollmentId;
    private final List<Map<String, Object>> pendingPrerequisites;

    public FinalExamPrerequisitesNotApprovedException(Long enrollmentId, List<Map<String, Object>> pendingPrerequisites) {
        super("Para rendir el final todas las correlativas deben estar aprobadas o tener una excepción aprobada.");
        this.enrollmentId = enrollmentId;
        this.pendingPrerequisites = List.copyOf(pendingPrerequisites);
    }

    @Override
    public Map<String, Object> details() {
        if (enrollmentId == null || pendingPrerequisites == null) {
            return Map.of();
        }
        return Map.of("enrollmentId", enrollmentId, "pendingPrerequisites", pendingPrerequisites);
    }
}
