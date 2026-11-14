package com.red_escuela.backend.dataimport.exception;

import com.red_escuela.backend.dataimport.domain.ImportEntityType;
import com.red_escuela.backend.shared.exception.ConflictException;
import com.red_escuela.backend.shared.exception.ReportsErrorCode;
import com.red_escuela.backend.shared.exception.ReportsErrorDetails;
import java.util.List;
import java.util.Map;

public class ImportRollbackBlockedException extends ConflictException implements ReportsErrorCode, ReportsErrorDetails {
    private final ImportEntityType entityType;
    private final String naturalKey;
    private final String reason;

    public ImportRollbackBlockedException(ImportEntityType entityType, String naturalKey, String reason) {
        super("No se puede deshacer la importación: " + reason);
        this.entityType = entityType;
        this.naturalKey = naturalKey;
        this.reason = reason;
    }

    public ImportEntityType entityType() {
        return entityType;
    }

    public String naturalKey() {
        return naturalKey;
    }

    public String reason() {
        return reason;
    }

    @Override
    public Map<String, Object> details() {
        return Map.of("blockers", List.of(Map.of("entityType", entityType.name(), "naturalKey", naturalKey, "reason", reason)));
    }
}
