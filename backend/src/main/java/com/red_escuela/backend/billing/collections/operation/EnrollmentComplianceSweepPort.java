package com.red_escuela.backend.billing.collections.operation;

import com.red_escuela.backend.billing.collections.domain.EnrollmentComplianceSweepReport;

import java.time.LocalDate;
import java.util.Optional;

public interface EnrollmentComplianceSweepPort {
    Optional<String> findNotApplicableCode(LocalDate businessDate);

    EnrollmentComplianceSweepReport sweepEnrollmentCompliance(LocalDate businessDate);
}
