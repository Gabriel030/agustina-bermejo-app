package com.red_escuela.backend.academicstructure.usecase.cohort;

import com.red_escuela.backend.academicstructure.dto.CohortResponsiblePreceptorResponse;

import java.util.List;

public interface ReplaceCohortResponsiblesUseCase {
    List<CohortResponsiblePreceptorResponse> replace(Long cohortId, List<Long> responsiblePreceptorIds);
}
