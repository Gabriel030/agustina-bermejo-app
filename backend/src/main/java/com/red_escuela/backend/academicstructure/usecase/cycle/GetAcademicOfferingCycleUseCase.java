package com.red_escuela.backend.academicstructure.usecase.cycle;

import com.red_escuela.backend.academicstructure.dto.AcademicOfferingCycleResponse;

public interface GetAcademicOfferingCycleUseCase {
    AcademicOfferingCycleResponse get(Long cycleId);
}
