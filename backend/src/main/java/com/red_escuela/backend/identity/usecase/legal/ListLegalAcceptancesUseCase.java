package com.red_escuela.backend.identity.usecase.legal;

import com.red_escuela.backend.identity.dto.LegalAcceptanceResponse;
import com.red_escuela.backend.shared.pagination.PageResponse;

public interface ListLegalAcceptancesUseCase {
    PageResponse<LegalAcceptanceResponse> listAcceptances(Long personId, String documentType, String version,
                                                          int page, int size);
}
