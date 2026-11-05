package com.red_escuela.backend.enrollment.usecase.prospect;

import com.red_escuela.backend.enrollment.dto.ProspectListItemResponse;
import com.red_escuela.backend.enrollment.service.ProspectService;
import com.red_escuela.backend.shared.UseCase;

import java.util.List;

@UseCase
public class ListProspectsService implements ListProspectsUseCase {

    private final ProspectService prospectService;

    public ListProspectsService(ProspectService prospectService) {
        this.prospectService = prospectService;
    }

    @Override
    public List<ProspectListItemResponse> list() {
        return prospectService.listProspects();
    }
}
