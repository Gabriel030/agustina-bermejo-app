package com.red_escuela.backend.billing.cohort.service;

import com.red_escuela.backend.academicstructure.entity.ProgramCohort;
import com.red_escuela.backend.billing.dto.ProgramCohortCatalogResponse;
import com.red_escuela.backend.identity.api.PersonNameFinder;
import com.red_escuela.backend.identity.api.PreceptorScopeFinder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class ProgramCohortCatalog {

    private final ProgramCohortLister programCohortLister;
    private final PreceptorScopeFinder preceptorScopeFinder;
    private final PersonNameFinder personNameFinder;
    private final Clock clock;

    public ProgramCohortCatalog(ProgramCohortLister programCohortLister, PreceptorScopeFinder preceptorScopeFinder,
                                               PersonNameFinder personNameFinder, Clock clock) {
        this.programCohortLister = programCohortLister;
        this.preceptorScopeFinder = preceptorScopeFinder;
        this.personNameFinder = personNameFinder;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
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
