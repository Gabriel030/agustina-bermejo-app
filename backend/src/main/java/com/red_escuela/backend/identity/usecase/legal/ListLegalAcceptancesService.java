package com.red_escuela.backend.identity.usecase.legal;

import com.red_escuela.backend.identity.dto.LegalAcceptanceResponse;
import com.red_escuela.backend.identity.specialist.LegalAcceptanceFinder;
import com.red_escuela.backend.shared.UseCase;
import com.red_escuela.backend.shared.pagination.PageResponse;

@UseCase
public class ListLegalAcceptancesService implements ListLegalAcceptancesUseCase {

    private final LegalAcceptanceFinder legalAcceptanceFinder;

    public ListLegalAcceptancesService(LegalAcceptanceFinder legalAcceptanceFinder) {
        this.legalAcceptanceFinder = legalAcceptanceFinder;
    }

    @Override
    public PageResponse<LegalAcceptanceResponse> listAcceptances(Long personId, String documentType,
                                                                 String version, int page, int size) {
        return legalAcceptanceFinder.search(personId, documentType, version, page, size);
    }
}
