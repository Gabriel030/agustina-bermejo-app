package com.red_escuela.backend.billing.usecase.dashboard;

import com.red_escuela.backend.billing.dto.DebtorSummaryResponse;

import com.red_escuela.backend.shared.pagination.PageResponse;

import java.util.List;

public interface ListDebtorsUseCase {
    List<DebtorSummaryResponse> list(String sortBy);

    PageResponse<DebtorSummaryResponse> page(String sortBy, int page, int size);
}
