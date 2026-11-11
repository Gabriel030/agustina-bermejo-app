package com.red_escuela.backend.evaluation.exception;

import com.red_escuela.backend.shared.exception.ConflictException;
import com.red_escuela.backend.shared.exception.ReportsErrorDetails;

import java.util.LinkedHashMap;
import java.util.Map;

public class ExamBoardFinalGradeRowRejectedException extends ConflictException implements ReportsErrorDetails {
    private final Long registrationId;
    private final String causeCode;
    private final Map<String, Object> causeDetails;

    public ExamBoardFinalGradeRowRejectedException(
            Long registrationId, String causeCode, Map<String, Object> causeDetails, Throwable cause) {
        super("No se guardó ninguna nota: la fila " + registrationId + " fue rechazada (" + causeCode + ")", cause);
        this.registrationId = registrationId;
        this.causeCode = causeCode;
        this.causeDetails = causeDetails;
    }

    @Override
    public Map<String, Object> details() {
        if (registrationId == null || causeCode == null) {
            return Map.of();
        }
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("registrationId", registrationId);
        details.put("causeCode", causeCode);
        if (causeDetails != null && !causeDetails.isEmpty()) {
            details.put("causeDetails", causeDetails);
        }
        return details;
    }
}
