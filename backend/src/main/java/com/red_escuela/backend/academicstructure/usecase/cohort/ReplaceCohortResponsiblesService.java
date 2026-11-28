package com.red_escuela.backend.academicstructure.usecase.cohort;

import com.red_escuela.backend.academicstructure.dto.CohortResponsiblePreceptorResponse;
import com.red_escuela.backend.academicstructure.service.CohortResponsiblesReplacement;
import com.red_escuela.backend.shared.UseCase;

import java.util.List;

@UseCase
public class ReplaceCohortResponsiblesService implements ReplaceCohortResponsiblesUseCase {

    private final CohortResponsiblesReplacement cohortResponsiblesReplacement;

    public ReplaceCohortResponsiblesService(CohortResponsiblesReplacement cohortResponsiblesReplacement) {
        this.cohortResponsiblesReplacement = cohortResponsiblesReplacement;
    }

    @Override
    public List<CohortResponsiblePreceptorResponse> replace(Long cohortId, List<Long> responsiblePreceptorIds) {
        return cohortResponsiblesReplacement.replace(cohortId, responsiblePreceptorIds);
    }
}
