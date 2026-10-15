package com.red_escuela.backend.academicstructure.api;

import com.red_escuela.backend.shared.domain.Currency;

import java.util.Optional;

public interface OfferingCurrencyLookup {
    Optional<Currency> findProgramCurrency(Long programId);

    Optional<Currency> findCourseCurrency(Long courseId);
}
