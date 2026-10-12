package com.red_escuela.backend.shared.billingterms;

import java.time.LocalDate;

public interface YearEnrollmentFeeTermsLookup {
    YearEnrollmentFeeTerms requireEnrollmentFeeTermsEffectiveOn(Long programId, Long termId, LocalDate date);
}
