package com.red_escuela.backend.academicstructure.usecase.cycle;

import com.red_escuela.backend.academicstructure.dto.AcademicOfferingCycleResponse;
import com.red_escuela.backend.academicstructure.entity.AcademicOfferingCycleStatus;

import java.util.List;

public interface ListAcademicOfferingCyclesUseCase {
    List<AcademicOfferingCycleResponse> list(Long programId, Long courseId, AcademicOfferingCycleStatus status);
}
