package com.red_escuela.backend.academicstructure.usecase.cohort;

import com.red_escuela.backend.academicstructure.api.ProgramCohortNotFoundException;
import com.red_escuela.backend.academicstructure.dto.CohortResponsiblePreceptorResponse;
import com.red_escuela.backend.academicstructure.repository.ProgramCohortRepository;
import com.red_escuela.backend.identity.api.CohortResponsibleAssigner;
import com.red_escuela.backend.identity.api.CurrentActorFinder;
import com.red_escuela.backend.identity.api.PersonNameFinder;
import com.red_escuela.backend.shared.UseCase;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;

@UseCase
public class ReplaceCohortResponsiblesService implements ReplaceCohortResponsiblesUseCase {

    private final ProgramCohortRepository cohortRepository;
    private final CohortResponsibleAssigner cohortResponsibleAssigner;
    private final CurrentActorFinder currentActorFinder;
    private final PersonNameFinder personNameFinder;

    public ReplaceCohortResponsiblesService(ProgramCohortRepository cohortRepository, CohortResponsibleAssigner cohortResponsibleAssigner,
                                            CurrentActorFinder currentActorFinder, PersonNameFinder personNameFinder) {
        this.cohortRepository = cohortRepository;
        this.cohortResponsibleAssigner = cohortResponsibleAssigner;
        this.currentActorFinder = currentActorFinder;
        this.personNameFinder = personNameFinder;
    }

    @Override
    @Transactional
    public List<CohortResponsiblePreceptorResponse> replace(Long cohortId, List<Long> responsiblePreceptorIds) {
        if (!cohortRepository.existsById(cohortId)) {
            throw new ProgramCohortNotFoundException(cohortId);
        }
        Set<Long> assigned = cohortResponsibleAssigner.replace(cohortId, responsiblePreceptorIds, currentActorFinder.currentPersonId());
        Map<Long, String> names = personNameFinder.findFullNamesByPersonId(assigned);
        return assigned.stream().map(id -> new CohortResponsiblePreceptorResponse(id, names.get(id))).toList();
    }
}
