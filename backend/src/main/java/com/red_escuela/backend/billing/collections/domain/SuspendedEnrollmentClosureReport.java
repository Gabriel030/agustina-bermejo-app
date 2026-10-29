package com.red_escuela.backend.billing.collections.domain;

import java.util.List;

public record SuspendedEnrollmentClosureReport(int candidates, int closed, List<EnrollmentClosureFailure> failures) {

    public SuspendedEnrollmentClosureReport {
        failures = List.copyOf(failures);
    }

    public record EnrollmentClosureFailure(Long enrollmentId, boolean rejectedByRule, String code, String message) {
    }
}
