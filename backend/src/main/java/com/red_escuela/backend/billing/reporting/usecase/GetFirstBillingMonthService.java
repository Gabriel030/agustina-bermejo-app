package com.red_escuela.backend.billing.reporting.usecase;

import com.red_escuela.backend.billing.reporting.service.FirstBillingMonthQuery;
import com.red_escuela.backend.shared.UseCase;
import org.springframework.security.access.prepost.PreAuthorize;

import java.time.YearMonth;
import java.util.Optional;

@UseCase
public class GetFirstBillingMonthService implements GetFirstBillingMonthUseCase {

    private final FirstBillingMonthQuery firstBillingMonthQuery;

    public GetFirstBillingMonthService(FirstBillingMonthQuery firstBillingMonthQuery) {
        this.firstBillingMonthQuery = firstBillingMonthQuery;
    }

    @Override
    @PreAuthorize("hasAnyRole('DIRECTOR', 'ADMINISTRATIVO')")
    public Optional<YearMonth> getFirstBillingMonth() {
        return firstBillingMonthQuery.findFirstBillingMonth();
    }
}
