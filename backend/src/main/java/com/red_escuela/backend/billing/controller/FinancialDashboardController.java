package com.red_escuela.backend.billing.controller;

import com.red_escuela.backend.billing.dto.DebtorSummaryResponse;
import com.red_escuela.backend.billing.dto.FinancialDashboardResponse;
import com.red_escuela.backend.billing.usecase.dashboard.GetFinancialDashboardUseCase;
import com.red_escuela.backend.billing.usecase.dashboard.ListDebtorsUseCase;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/financial-dashboard")
public class FinancialDashboardController {

    private final GetFinancialDashboardUseCase getFinancialDashboard;
    private final ListDebtorsUseCase listDebtors;

    public FinancialDashboardController(GetFinancialDashboardUseCase getFinancialDashboard,
                                        ListDebtorsUseCase listDebtors) {
        this.getFinancialDashboard = getFinancialDashboard;
        this.listDebtors = listDebtors;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('DIRECTOR', 'ADMINISTRATIVO')")
    public FinancialDashboardResponse getDashboard() {
        return getFinancialDashboard.execute();
    }

    @GetMapping("/debtors")
    @PreAuthorize("hasRole('DIRECTOR')")
    public List<DebtorSummaryResponse> getDebtors(
            @RequestParam(defaultValue = "DEBT_AGE") String sortBy) {
        return listDebtors.list(sortBy);
    }
}
