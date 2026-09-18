package com.red_escuela.backend.billing.dto;

import java.math.BigDecimal;

public record DebtorSummaryResponse(
        Long studentId,
        String fullName,
        String dni,
        String enrollmentName,
        BigDecimal totalDebt,
        int debtAgeDays
) {}
