package com.red_escuela.backend.billing.api;

import com.red_escuela.backend.academicstructure.api.HistoricalCycleRecorder;
import com.red_escuela.backend.academicstructure.api.HistoricalProgramRecorder;
import com.red_escuela.backend.academicstructure.domain.TermType;
import com.red_escuela.backend.academicstructure.entity.Program;
import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.academicstructure.domain.CohortIntake;
import com.red_escuela.backend.academicstructure.entity.ProgramCohort;
import com.red_escuela.backend.billing.entity.ProgramYearBillingTemplate;
import com.red_escuela.backend.billing.cohort.exception.HistoricalCohortReferenceMissingException;
import com.red_escuela.backend.academicstructure.api.ProgramCohortFinder;
import com.red_escuela.backend.academicstructure.api.ProgramCohortRecorder;
import com.red_escuela.backend.billing.repository.ProgramYearBillingTemplateRepository;
import com.red_escuela.backend.identity.api.CurrentActorFinder;
import com.red_escuela.backend.identity.entity.Person;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class HistoricalProgramCohortRecorder {
    private static final int FIRST_INTAKE_START_MONTH = 3;
    private static final int SECOND_INTAKE_START_MONTH = 8;

    private final ProgramYearBillingTemplateRepository templateRepository;
    private final ProgramCohortFinder cohortFinder;
    private final ProgramCohortRecorder cohortRecorder;
    private final HistoricalProgramRecorder programRecorder;
    private final HistoricalCycleRecorder cycleRecorder;
    private final CurrentActorFinder currentActorFinder;
    private final EntityManager entityManager;

    public HistoricalProgramCohortRecorder(ProgramYearBillingTemplateRepository templateRepository,
            ProgramCohortFinder cohortFinder, ProgramCohortRecorder cohortRecorder, HistoricalProgramRecorder programRecorder,
            HistoricalCycleRecorder cycleRecorder, CurrentActorFinder currentActorFinder, EntityManager entityManager) {
        this.templateRepository = templateRepository;
        this.cohortFinder = cohortFinder;
        this.cohortRecorder = cohortRecorder;
        this.programRecorder = programRecorder;
        this.cycleRecorder = cycleRecorder;
        this.currentActorFinder = currentActorFinder;
        this.entityManager = entityManager;
    }

    @Transactional
    public Long findOrCreateCohortId(HistoricalProgramCohort historical) {
        return findOrCreateCohort(historical).getId();
    }

    private ProgramCohort findOrCreateCohort(HistoricalProgramCohort historical) {
        ProgramYearBillingTemplate template = findOrCreateTemplate(historical);
        CohortIntake intake = CohortIntake.valueOf(historical.intake().name());
        return cohortFinder.findByTemplateIdAndIntake(template.getId(), intake)
                .orElseGet(() -> cohortRecorder.save(newCohort(template, historical, intake)));
    }

    private ProgramYearBillingTemplate findOrCreateTemplate(HistoricalProgramCohort historical) {
        Long programId = programRecorder.findProgramIdByName(historical.programName())
                .orElseThrow(() -> new HistoricalCohortReferenceMissingException(historical.programName()));
        Long termId = cycleRecorder.findTermId(historical.year(), TermType.ANNUAL)
                .orElseThrow(() -> new HistoricalCohortReferenceMissingException("año lectivo " + historical.year()));
        return templateRepository.findByProgramIdAndTermId(programId, termId)
                .orElseGet(() -> templateRepository.save(newTemplate(programId, termId)));
    }

    private ProgramYearBillingTemplate newTemplate(Long programId, Long termId) {
        ProgramYearBillingTemplate template = new ProgramYearBillingTemplate();
        template.setProgram(entityManager.getReference(Program.class, programId));
        template.setTerm(entityManager.getReference(Term.class, termId));
        template.setCreatedBy(actor());
        return template;
    }

    private ProgramCohort newCohort(ProgramYearBillingTemplate template, HistoricalProgramCohort historical, CohortIntake intake) {
        int startMonth = intake == CohortIntake.FIRST ? FIRST_INTAKE_START_MONTH : SECOND_INTAKE_START_MONTH;
        LocalDate closedAdmissionDay = LocalDate.of(historical.year(), 1, 1);
        ProgramCohort cohort = new ProgramCohort();
        cohort.setTemplateId(template.getId());
        cohort.setProgramId(template.programId());
        cohort.setTermId(template.termId());
        cohort.setIntake(intake);
        cohort.setAdmissionStart(closedAdmissionDay);
        cohort.setAdmissionEnd(closedAdmissionDay);
        cohort.setCursadaStart(LocalDate.of(historical.year(), startMonth, 1));
        cohort.setCreatedBy(actor());
        return cohort;
    }

    private Person actor() {
        return entityManager.getReference(Person.class, currentActorFinder.currentPersonId());
    }
}
