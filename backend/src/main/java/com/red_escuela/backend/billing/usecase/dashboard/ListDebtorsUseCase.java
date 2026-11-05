package com.red_escuela.backend.billing.usecase.dashboard;

import com.red_escuela.backend.billing.dto.DebtorSummaryResponse;

import java.util.List;

public interface ListDebtorsUseCase {
    List<DebtorSummaryResponse> list(String sortBy);
}
