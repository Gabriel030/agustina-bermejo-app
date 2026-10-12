package com.red_escuela.backend.billing.dto;

import com.red_escuela.backend.shared.domain.Money;

import java.util.List;

public record FinancialDashboardResponse(
        int studentsInDebt,
        int studentsUpToDate,
        List<CurrencyTotals> totalsByCurrency
) {
    public record CurrencyTotals(
            Money totalDebt,
            Money monthlyProjected,
            Money monthlyCollected,
            Money monthlyDebt
    ) {}
}
