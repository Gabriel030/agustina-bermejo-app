package com.red_escuela.backend.billing.configuration.policy;

import com.red_escuela.backend.billing.configuration.domain.AnnualUpfrontSettings;

import java.time.LocalDate;

public record AnnualUpfrontCheck(AnnualUpfrontSettings settings, LocalDate termStart) {
}
