package com.red_escuela.backend.platform.exception;

import com.red_escuela.backend.platform.model.InstituteSettingCode;
import com.red_escuela.backend.shared.exception.ConflictException;
import com.red_escuela.backend.shared.exception.ReportsErrorDetails;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

public class StaleInstituteSettingVersionException extends ConflictException implements ReportsErrorDetails {

    private final LocalDateTime changedAt;
    private final Long changedById;
    private final String changedByName;

    public StaleInstituteSettingVersionException(InstituteSettingCode code, long expected, long actual, LocalDateTime changedAt, Long changedById, String changedByName) {
        super(code + " cambió mientras lo editabas (versión pedida " + expected + ", actual " + actual + ")");
        this.changedAt = changedAt;
        this.changedById = changedById;
        this.changedByName = changedByName;
    }

    @Override
    public Map<String, Object> details() {
        Map<String, Object> details = new LinkedHashMap<>();
        if (changedAt != null) {
            details.put(StaleFeatureVersionException.CHANGED_AT_KEY, changedAt.toString());
        }
        if (changedById != null) {
            details.put(StaleFeatureVersionException.CHANGED_BY_ID_KEY, changedById);
        }
        if (changedByName != null) {
            details.put(StaleFeatureVersionException.CHANGED_BY_NAME_KEY, changedByName);
        }
        return details;
    }
}
