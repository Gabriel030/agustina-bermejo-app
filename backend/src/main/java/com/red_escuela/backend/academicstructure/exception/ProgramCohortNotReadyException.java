package com.red_escuela.backend.academicstructure.exception;

import com.red_escuela.backend.shared.exception.ConflictException;
import com.red_escuela.backend.shared.exception.ReportsErrorDetails;

import java.util.List;
import java.util.Map;

public class ProgramCohortNotReadyException extends ConflictException implements ReportsErrorDetails {
    private final List<String> missingItems;

    public ProgramCohortNotReadyException(Long cohortId, List<String> missingItems) {
        super("La cohorte " + cohortId + " no se puede abrir: faltan " + missingItems);
        this.missingItems = List.copyOf(missingItems);
    }

    @Override
    public Map<String, Object> details() {
        return missingItems == null ? Map.of() : Map.of("missingItems", missingItems);
    }
}
