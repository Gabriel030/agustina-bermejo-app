package com.red_escuela.backend.billing.dto;

import java.math.BigDecimal;

public record FinancialDashboardResponse(
        int studentsInDebt,
        int studentsUpToDate,
        BigDecimal totalDebt,
        BigDecimal monthlyProjected,
        BigDecimal monthlyCollected,
        BigDecimal monthlyDebt
) {}
