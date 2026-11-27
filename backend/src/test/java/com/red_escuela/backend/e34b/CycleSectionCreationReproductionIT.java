package com.red_escuela.backend.e34b;

import com.red_escuela.backend.academicstructure.CycleTestFixtures;
import com.red_escuela.backend.academicstructure.domain.SectionShift;
import com.red_escuela.backend.academicstructure.entity.AcademicOfferingCycle;
import com.red_escuela.backend.academicstructure.entity.AcademicOfferingCycleStatus;
import com.red_escuela.backend.academicstructure.entity.Program;
import com.red_escuela.backend.academicstructure.entity.Subject;
import com.red_escuela.backend.academicstructure.entity.SubjectStatus;
import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.academicstructure.service.CreateCycleSectionTransaction;
import com.red_escuela.backend.evaluation.repository.SectionEvaluationConfigRepository;
import com.red_escuela.backend.shared.domain.Percentage;
import com.red_escuela.backend.shared.domain.StatusType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CycleSectionCreationReproductionIT extends ReproductionFixture {

    @Autowired
    private CreateCycleSectionTransaction createCycleSectionTransaction;
    @Autowired
    private SectionEvaluationConfigRepository evaluationConfigRepository;

    private CycleTestFixtures cycleFixtures;
    private Program program;
    private AcademicOfferingCycle cycle;

    @BeforeEach
    void draftCycleOfAProgram() {
        cycleFixtures = new CycleTestFixtures(entityManager);
        program = cycleFixtures.program();
        Term term = cycleFixtures.term(2041);
        cycle = cycleFixtures.programCycle(program, term, AcademicOfferingCycleStatus.DRAFT);
        flushAndClear();
    }

    private Long createSectionOf(Subject subject) {
        return createCycleSectionTransaction.createSection(cycle.getId(), "Comision A", subject.getId(), null, 30, null,
                Percentage.ofPoints(new BigDecimal("25")), SectionShift.MORNING, null, LocalDateTime.of(2026, 9, 1, 10, 0));
    }

    @Test
    @Disabled("E34-B-P4")
    void sectionCreatedFromACycleStartsWithADraftEvaluationConfig() {
        Subject subject = cycleFixtures.subject();
        programSubject(program, subject, 4, StatusType.ACTIVE);
        flushAndClear();

        Long sectionId = createSectionOf(subject);
        flushAndClear();

        assertThat(evaluationConfigRepository.findBySubjectSectionId(sectionId)).isPresent();
    }

    @Test
    @Disabled("E34-B-P4")
    void sectionCannotBeCreatedForASubjectOutsideThePlanOfTheCycleProgram() {
        Subject subject = cycleFixtures.subject();

        assertThatThrownBy(() -> createSectionOf(subject)).isInstanceOf(RuntimeException.class);
    }

    @Test
    @Disabled("E34-B-P4")
    void sectionCannotBeCreatedForAnInactiveOrRejectedSubject() {
        Subject inactive = cycleFixtures.subject();
        inactive.setStatus(SubjectStatus.INACTIVE);
        Subject rejected = cycleFixtures.subject();
        rejected.setStatus(SubjectStatus.REJECTED);
        Subject pending = cycleFixtures.subject();
        pending.setStatus(SubjectStatus.PENDING_APPROVAL);
        for (Subject subject : new Subject[] {inactive, rejected, pending}) {
            programSubject(program, subject, 4, StatusType.ACTIVE);
        }
        flushAndClear();

        assertThatThrownBy(() -> createSectionOf(inactive)).as("inactive").isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> createSectionOf(rejected)).as("rejected").isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> createSectionOf(pending)).as("pending approval").isInstanceOf(RuntimeException.class);
    }
}
