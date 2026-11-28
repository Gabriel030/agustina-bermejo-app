package com.red_escuela.backend.billing.usecase.cohort;

import com.red_escuela.backend.academicstructure.entity.ProgramCohort;
import com.red_escuela.backend.billing.cohort.service.ProgramCohortLister;
import com.red_escuela.backend.billing.dto.ProgramCohortCatalogResponse;
import com.red_escuela.backend.identity.api.PersonNameFinder;
import com.red_escuela.backend.identity.api.PreceptorScopeFinder;
import com.red_escuela.backend.shared.UseCase;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

@UseCase
public class ListProgramCohortsForProgramService implements ListProgramCohortsForProgramUseCase {

    private final ProgramCohortLister programCohortLister;
    private final PreceptorScopeFinder preceptorScopeFinder;
    private final PersonNameFinder personNameFinder;
    private final Clock clock;

    public ListProgramCohortsForProgramService(ProgramCohortLister programCohortLister, PreceptorScopeFinder preceptorScopeFinder,
                                               PersonNameFinder personNameFinder, Clock clock) {
        this.programCohortLister = programCohortLister;
        this.preceptorScopeFinder = preceptorScopeFinder;
        this.personNameFinder = personNameFinder;
        this.clock = clock;
    }

    @Override
    public List<ProgramCohortCatalogResponse> list(Long programId) {
        LocalDate today = LocalDate.now(clock);
        return programCohortLister.listForProgram(programId).stream()
                .map(cohort -> ProgramCohortCatalogResponse.from(cohort, programId, today, responsiblesOf(cohort)))
                .toList();
    }

    private List<ProgramCohortCatalogResponse.ResponsiblePreceptor> responsiblesOf(ProgramCohort cohort) {
        Set<Long> personIds = preceptorScopeFinder.responsiblePersonIdsOfCohort(cohort.getId());
        Map<Long, String> names = personNameFinder.findFullNamesByPersonId(personIds);
        return personIds.stream().sorted(Comparator.naturalOrder())
                .map(id -> new ProgramCohortCatalogResponse.ResponsiblePreceptor(id, names.get(id))).toList();
    }
}
