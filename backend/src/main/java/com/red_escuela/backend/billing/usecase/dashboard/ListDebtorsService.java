package com.red_escuela.backend.billing.usecase.dashboard;

import com.red_escuela.backend.billing.dto.DebtorSummaryResponse;
import com.red_escuela.backend.billing.reporting.service.DebtorListingReader;
import com.red_escuela.backend.shared.UseCase;
import java.util.List;

@UseCase
public class ListDebtorsService implements ListDebtorsUseCase {
    private final DebtorListingReader debtorListingReader;

    public ListDebtorsService(DebtorListingReader debtorListingReader) {
        this.debtorListingReader = debtorListingReader;
    }

    @Override
    public List<DebtorSummaryResponse> list(String sortBy) {
        return debtorListingReader.listDebtors(sortBy);
    }
}
