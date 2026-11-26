package com.red_escuela.backend.enrollment.api;

import java.time.LocalDate;

public interface EnrollmentFeeExemptionTermsPort {

    EnrollmentFeeExemptionTermsView requireTermsForExemption(Long templateId, Long programId, int slot, LocalDate effectiveOn);
}
