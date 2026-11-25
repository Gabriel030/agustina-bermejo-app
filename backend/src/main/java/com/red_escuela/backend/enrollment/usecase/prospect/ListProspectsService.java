package com.red_escuela.backend.enrollment.usecase.prospect;

import com.red_escuela.backend.enrollment.dto.ProspectListItemResponse;
import com.red_escuela.backend.enrollment.service.ProspectService;
import com.red_escuela.backend.shared.UseCase;
import com.red_escuela.backend.shared.pagination.PageResponse;


@UseCase
public class ListProspectsService implements ListProspectsUseCase {

    private final ProspectService prospectService;

    public ListProspectsService(ProspectService prospectService) {
        this.prospectService = prospectService;
    }

    @Override
    public PageResponse<ProspectListItemResponse> list(String search, int page, int size) {
        return prospectService.listProspects(search, page, size);
    }
}
