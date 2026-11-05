package com.red_escuela.backend.billing.usecase.dashboard;

import com.red_escuela.backend.billing.dto.DebtorSummaryResponse;
import com.red_escuela.backend.shared.pagination.PageResponse;


public interface ListDebtorsUseCase {
    PageResponse<DebtorSummaryResponse> list(String sortBy, int page, int size);
}
