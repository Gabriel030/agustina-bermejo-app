package com.red_escuela.backend.enrollment.usecase.prospect;

import com.red_escuela.backend.enrollment.dto.CreateProspectRequest;
import com.red_escuela.backend.enrollment.dto.ProspectResponse;

public interface CreateProspectByStaffUseCase {
    ProspectResponse create(CreateProspectRequest request);
}
