package com.red_escuela.backend.billing.dto;

import com.red_escuela.backend.shared.domain.Money;

public record DebtorSummaryResponse(
        Long studentId,
        String fullName,
        String dni,
        String enrollmentName,
        Money totalDebt,
        int debtAgeDays
) {}
