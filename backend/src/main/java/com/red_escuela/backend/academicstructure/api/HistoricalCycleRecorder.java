package com.red_escuela.backend.academicstructure.api;

import com.red_escuela.backend.academicstructure.domain.TermType;
import com.red_escuela.backend.academicstructure.entity.AcademicOfferingCycle;
import com.red_escuela.backend.academicstructure.entity.AcademicOfferingCycleStatus;
import com.red_escuela.backend.academicstructure.entity.Program;
import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.academicstructure.exception.HistoricalReferenceMissingException;
import com.red_escuela.backend.academicstructure.repository.AcademicOfferingCycleRepository;
import com.red_escuela.backend.academicstructure.repository.ProgramRepository;
import com.red_escuela.backend.academicstructure.repository.TermRepository;
import com.red_escuela.backend.identity.api.CurrentActorFinder;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.shared.domain.StatusType;
import com.red_escuela.backend.shared.time.ApplicationTime;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.time.LocalTime;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class HistoricalCycleRecorder {
    private final TermRepository termRepository;
    private final AcademicOfferingCycleRepository cycleRepository;
    private final ProgramRepository programRepository;
    private final CurrentActorFinder currentActorFinder;
    private final EntityManager entityManager;

    public HistoricalCycleRecorder(TermRepository termRepository, AcademicOfferingCycleRepository cycleRepository,
            ProgramRepository programRepository, CurrentActorFinder currentActorFinder, EntityManager entityManager) {
        this.termRepository = termRepository;
        this.cycleRepository = cycleRepository;
        this.programRepository = programRepository;
        this.currentActorFinder = currentActorFinder;
        this.entityManager = entityManager;
    }

    @Transactional(readOnly = true)
    public boolean cycleExists(int year, TermType termType) {
        return termRepository.findFirstByYearAndTypeOrderByIdAsc(year, termType).isPresent();
    }

    @Transactional
    public Long recordCycle(HistoricalCycle historical) {
        Program program = programRepository.findFirstByName(historical.programName())
                .orElseThrow(() -> new HistoricalReferenceMissingException(historical.programName()));
        Term term = termRepository.findFirstByYearAndTypeOrderByIdAsc(historical.year(), historical.termType())
                .orElseGet(() -> termRepository.save(newTerm(historical)));
        Person actor = entityManager.getReference(Person.class, currentActorFinder.currentPersonId());
        LocalDateTime now = ApplicationTime.now();
        AcademicOfferingCycle cycle = new AcademicOfferingCycle();
        cycle.setTerm(term);
        cycle.setProgram(program);
        cycle.setStatus(AcademicOfferingCycleStatus.CLOSED);
        cycle.setCreatedBy(actor);
        cycle.setCreatedAt(now);
        cycle.setDecidedBy(actor);
        cycle.setDecidedAt(now);
        return cycleRepository.save(cycle).getId();
    }

    private static Term newTerm(HistoricalCycle historical) {
        Term term = new Term();
        term.setYear(historical.year());
        term.setType(historical.termType());
        term.setDescription(historical.description());
        term.setStartDate(historical.startDate().atStartOfDay());
        term.setEndDate(historical.endDate().atTime(LocalTime.of(23, 59)));
        term.setEnrollmentStart(term.getStartDate());
        term.setEnrollmentEnd(term.getEndDate());
        term.setStatus(StatusType.INACTIVE);
        return term;
    }
}
