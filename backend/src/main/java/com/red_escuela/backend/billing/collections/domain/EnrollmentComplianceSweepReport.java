package com.red_escuela.backend.billing.collections.domain;

import java.util.List;

public record EnrollmentComplianceSweepReport(int candidates, List<EnrollmentComplianceFailure> failures) {

    public EnrollmentComplianceSweepReport {
        failures = List.copyOf(failures);
    }

    public int failed() {
        return failures.size();
    }

    public int processed() {
        return Math.max(0, candidates - failures.size());
    }

    public record EnrollmentComplianceFailure(EnrollmentKind kind, Long enrollmentId, String message) {
    }
}
