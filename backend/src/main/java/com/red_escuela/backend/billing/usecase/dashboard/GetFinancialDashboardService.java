package com.red_escuela.backend.billing.usecase.dashboard;

import com.red_escuela.backend.billing.dto.FinancialDashboardResponse;
import com.red_escuela.backend.billing.reporting.service.FinancialDashboardReader;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class GetFinancialDashboardService implements GetFinancialDashboardUseCase {
    private final FinancialDashboardReader financialDashboardReader;

    public GetFinancialDashboardService(FinancialDashboardReader financialDashboardReader) {
        this.financialDashboardReader = financialDashboardReader;
    }

    @Override
    public FinancialDashboardResponse execute() {
        return financialDashboardReader.readFinancialDashboard();
    }
}
