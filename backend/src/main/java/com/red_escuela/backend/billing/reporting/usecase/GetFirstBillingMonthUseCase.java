package com.red_escuela.backend.billing.reporting.usecase;

import java.time.YearMonth;
import java.util.Optional;

public interface GetFirstBillingMonthUseCase {
    Optional<YearMonth> getFirstBillingMonth();
}
