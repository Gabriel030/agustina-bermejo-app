package com.red_escuela.backend.enrollment.exception;

import com.red_escuela.backend.shared.exception.ConflictException;
import com.red_escuela.backend.shared.exception.ReportsErrorDetails;

import java.util.LinkedHashMap;
import java.util.Map;

public class SectionScheduleOverlapException extends ConflictException implements ReportsErrorDetails {
    public static final String CONFLICTING_SUBJECT_KEY = "conflictingSubject";
    public static final String DAY_KEY = "day";
    public static final String FROM_KEY = "from";
    public static final String TO_KEY = "to";

    private final String conflictingSubjectName;
    private final String dayLabel;
    private final String from;
    private final String to;

    public SectionScheduleOverlapException(String conflictingSubjectName, String dayLabel, String from, String to) {
        super("El horario de la comisión elegida se superpone con " + conflictingSubjectName
                + " (" + dayLabel + " de " + from + " a " + to + ")");
        this.conflictingSubjectName = conflictingSubjectName;
        this.dayLabel = dayLabel;
        this.from = from;
        this.to = to;
    }

    public String conflictingSubjectName() {
        return conflictingSubjectName;
    }

    @Override
    public Map<String, Object> details() {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put(CONFLICTING_SUBJECT_KEY, conflictingSubjectName);
        details.put(DAY_KEY, dayLabel);
        details.put(FROM_KEY, from);
        details.put(TO_KEY, to);
        return details;
    }
}
