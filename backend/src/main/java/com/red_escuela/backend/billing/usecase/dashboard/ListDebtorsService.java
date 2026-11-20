package com.red_escuela.backend.billing.usecase.dashboard;

import com.red_escuela.backend.billing.dto.DebtorSummaryResponse;
import com.red_escuela.backend.billing.reporting.service.DebtorPageReader;
import com.red_escuela.backend.shared.UseCase;
import com.red_escuela.backend.shared.pagination.PageResponse;

@UseCase
public class ListDebtorsService implements ListDebtorsUseCase {
    private final DebtorPageReader debtorPageReader;

    public ListDebtorsService(DebtorPageReader debtorPageReader) {
        this.debtorPageReader = debtorPageReader;
    }

    @Override
    public PageResponse<DebtorSummaryResponse> page(String sortBy, int page, int size) {
        return debtorPageReader.findDebtorsPage(sortBy, page, size);
    }
}
