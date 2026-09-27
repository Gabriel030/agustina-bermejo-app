package com.red_escuela.backend.academicstructure.usecase.cycle;

import com.red_escuela.backend.academicstructure.dto.AcademicOfferingCycleResponse;
import com.red_escuela.backend.academicstructure.dto.CreateAcademicOfferingCycleRequest;

public interface CreateAcademicOfferingCycleUseCase {
    AcademicOfferingCycleResponse create(CreateAcademicOfferingCycleRequest request);
}
