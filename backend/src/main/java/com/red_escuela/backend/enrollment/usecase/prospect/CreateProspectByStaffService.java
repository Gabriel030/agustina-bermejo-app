package com.red_escuela.backend.enrollment.usecase.prospect;

import com.red_escuela.backend.enrollment.dto.CreateProspectRequest;
import com.red_escuela.backend.enrollment.service.ProspectService;
import com.red_escuela.backend.enrollment.dto.ProspectResponse;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class CreateProspectByStaffService implements CreateProspectByStaffUseCase {

    private final ProspectService prospectService;

    public CreateProspectByStaffService(ProspectService prospectService) {
        this.prospectService = prospectService;
    }

    @Override
    public ProspectResponse create(CreateProspectRequest request) {
        return ProspectResponse.from(prospectService.createProspectByStaff(request));
    }
}
