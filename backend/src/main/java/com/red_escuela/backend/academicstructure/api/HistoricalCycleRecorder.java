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
import java.util.List;
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
    public boolean termExists(int year, TermType termType) {
        return termRepository.findFirstByYearAndTypeOrderByIdAsc(year, termType).isPresent();
    }

    @Transactional(readOnly = true)
    public java.util.Optional<Long> findTermId(int year, TermType termType) {
        return termRepository.findFirstByYearAndTypeOrderByIdAsc(year, termType).map(Term::getId);
    }

    @Transactional(readOnly = true)
    public boolean cycleExists(int year, TermType termType, String programName) {
        return termRepository.findFirstByYearAndTypeOrderByIdAsc(year, termType)
                .flatMap(term -> programRepository.findFirstByName(programName)
                        .flatMap(program -> cycleRepository.findFirstByTermIdAndProgramIdOrderByIdAsc(term.getId(), program.getId())))
                .isPresent();
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

    @Transactional
    public void discardCycle(Long cycleId) {
        cycleRepository.deleteById(cycleId);
        cycleRepository.flush();
    }

    @Transactional(readOnly = true)
    public Long termIdOfCycle(Long cycleId) {
        return cycleRepository.findById(cycleId).orElseThrow().getTerm().getId();
    }

    @Transactional
    public void discardTermIfUnused(Long termId) {
        entityManager.flush();
        if (isTermReferenced(termId)) {
            return;
        }
        termRepository.deleteById(termId);
        termRepository.flush();
    }

    @SuppressWarnings("unchecked")
    private boolean isTermReferenced(Long termId) {
        List<Object[]> references = entityManager.createNativeQuery("""
                select c.conrelid::regclass::text, a.attname
                from pg_constraint c
                join pg_attribute a on a.attrelid = c.conrelid and a.attnum = c.conkey[1]
                where c.contype = 'f' and c.confrelid = 'term'::regclass
                """).getResultList();
        return references.stream().anyMatch(reference -> ((Number) entityManager.createNativeQuery(
                "select count(*) from " + reference[0] + " where \"" + reference[1] + "\" = :id")
                .setParameter("id", termId).getSingleResult()).longValue() > 0);
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
