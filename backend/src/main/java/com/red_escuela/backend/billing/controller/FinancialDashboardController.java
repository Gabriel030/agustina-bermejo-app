package com.red_escuela.backend.billing.controller;

import com.red_escuela.backend.shared.permission.RequiresPermission;
import com.red_escuela.backend.billing.dto.DebtorSummaryResponse;
import com.red_escuela.backend.billing.dto.FinancialDashboardResponse;
import com.red_escuela.backend.billing.usecase.dashboard.GetFinancialDashboardUseCase;
import com.red_escuela.backend.billing.usecase.dashboard.ListDebtorsUseCase;
import com.red_escuela.backend.shared.module.RequiresModule;
import com.red_escuela.backend.shared.pagination.PageResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiresModule(RequiresModule.BILLING)
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
    @RequiresPermission(code = "billing.read_financial_dashboard", legacyRoles = "ROLE_DIRECTOR,ROLE_ADMINISTRATIVO")
    public FinancialDashboardResponse getDashboard() {
        return getFinancialDashboard.execute();
    }

    @GetMapping("/debtors/page")
    @RequiresPermission(code = "billing.read_debtors", legacyRoles = "ROLE_DIRECTOR")
    public PageResponse<DebtorSummaryResponse> getDebtorsPage(
            @RequestParam(defaultValue = "DEBT_AGE") String sortBy,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        return listDebtors.page(sortBy, page, size);
    }
}
