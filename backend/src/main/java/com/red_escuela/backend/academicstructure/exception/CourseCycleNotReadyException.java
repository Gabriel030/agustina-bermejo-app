package com.red_escuela.backend.academicstructure.exception;

import com.red_escuela.backend.shared.exception.ConflictException;
import com.red_escuela.backend.shared.exception.ReportsErrorDetails;

import java.util.List;
import java.util.Map;

public class CourseCycleNotReadyException extends ConflictException implements ReportsErrorDetails {
    private final List<String> missingItems;
    private final List<String> reasons;

    public CourseCycleNotReadyException(Long cycleId, List<String> missingItems, List<String> reasons) {
        super("El ciclo " + cycleId + " no se puede aprobar: faltan " + missingItems);
        this.missingItems = List.copyOf(missingItems);
        this.reasons = List.copyOf(reasons);
    }

    @Override
    public Map<String, Object> details() {
        return Map.of("missingItems", missingItems, "reasons", reasons);
    }
}
