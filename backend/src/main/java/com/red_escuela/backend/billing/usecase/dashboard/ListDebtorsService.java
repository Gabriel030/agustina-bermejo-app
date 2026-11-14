package com.red_escuela.backend.billing.usecase.dashboard;

import com.red_escuela.backend.billing.dto.DebtorSummaryResponse;
import com.red_escuela.backend.billing.reporting.service.DebtorListingReader;
import com.red_escuela.backend.billing.reporting.service.DebtorPageReader;
import com.red_escuela.backend.shared.UseCase;
import com.red_escuela.backend.shared.pagination.PageResponse;
import java.util.List;

@UseCase
public class ListDebtorsService implements ListDebtorsUseCase {
    private final DebtorListingReader debtorListingReader;
    private final DebtorPageReader debtorPageReader;

    public ListDebtorsService(DebtorListingReader debtorListingReader, DebtorPageReader debtorPageReader) {
        this.debtorListingReader = debtorListingReader;
        this.debtorPageReader = debtorPageReader;
    }

    @Override
    public List<DebtorSummaryResponse> list(String sortBy) {
        return debtorListingReader.listDebtors(sortBy);
    }

    @Override
    public PageResponse<DebtorSummaryResponse> page(String sortBy, int page, int size) {
        return debtorPageReader.findDebtorsPage(sortBy, page, size);
    }
}
