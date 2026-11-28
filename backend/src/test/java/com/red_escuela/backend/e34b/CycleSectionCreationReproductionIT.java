package com.red_escuela.backend.e34b;

import com.red_escuela.backend.academicstructure.CycleTestFixtures;
import com.red_escuela.backend.academicstructure.domain.SectionShift;
import com.red_escuela.backend.academicstructure.entity.AcademicOfferingCycle;
import com.red_escuela.backend.academicstructure.entity.AcademicOfferingCycleStatus;
import com.red_escuela.backend.academicstructure.entity.Program;
import com.red_escuela.backend.academicstructure.entity.Subject;
import com.red_escuela.backend.academicstructure.domain.TermType;
import com.red_escuela.backend.academicstructure.entity.SubjectRecurrence;
import com.red_escuela.backend.academicstructure.entity.SubjectStatus;
import com.red_escuela.backend.academicstructure.entity.ProgramSubject;
import com.red_escuela.backend.academicstructure.exception.SubjectNotInProgramPlanException;
import com.red_escuela.backend.academicstructure.exception.SubjectNotOpenableForSectionsException;
import com.red_escuela.backend.academicstructure.exception.SubjectRecurrenceIncompatibleWithTermException;
import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.academicstructure.service.CreateCycleSectionTransaction;
import com.red_escuela.backend.evaluation.repository.SectionEvaluationConfigRepository;
import com.red_escuela.backend.shared.domain.Percentage;
import com.red_escuela.backend.shared.domain.StatusType;
import org.junit.jupiter.api.BeforeEach;
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
    void sectionCreatedFromACycleStartsWithADraftEvaluationConfig() {
        Subject subject = cycleFixtures.subject();
        programSubject(program, subject, 4, StatusType.ACTIVE);
        flushAndClear();

        Long sectionId = createSectionOf(subject);
        flushAndClear();

        assertThat(evaluationConfigRepository.findBySubjectSectionId(sectionId)).isPresent();
    }

    @Test
    void sectionCannotBeCreatedForASubjectOutsideThePlanOfTheCycleProgram() {
        Subject subject = cycleFixtures.subject();
        flushAndClear();

        assertThatThrownBy(() -> createSectionOf(subject)).isInstanceOf(SubjectNotInProgramPlanException.class);
    }

    @Test
    void sectionCannotBeCreatedForASubjectOfAnotherProgramPlan() {
        Subject subject = cycleFixtures.subject();
        programSubject(cycleFixtures.program(), subject, 4, StatusType.ACTIVE);
        flushAndClear();

        assertThatThrownBy(() -> createSectionOf(subject)).isInstanceOf(SubjectNotInProgramPlanException.class);
    }

    @Test
    void sectionCannotBeCreatedForASubjectRemovedFromThePlan() {
        Subject subject = cycleFixtures.subject();
        programSubject(program, subject, 4, StatusType.INACTIVE);
        flushAndClear();

        assertThatThrownBy(() -> createSectionOf(subject)).isInstanceOf(SubjectNotInProgramPlanException.class);
    }

    @Test
    void sectionCannotBeCreatedForASubjectThatIsNotActive() {
        for (SubjectStatus status : new SubjectStatus[] {SubjectStatus.DRAFT, SubjectStatus.PENDING_APPROVAL,
                SubjectStatus.REJECTED, SubjectStatus.INACTIVE}) {
            Subject subject = cycleFixtures.subject();
            subject.setStatus(status);
            programSubject(program, subject, 4, StatusType.ACTIVE);
            flushAndClear();

            assertThatThrownBy(() -> createSectionOf(subject)).as(status.name())
                    .isInstanceOf(SubjectNotOpenableForSectionsException.class);
        }
    }

    @Test
    void annualSubjectCannotBeOfferedInASemesterTerm() {
        Subject subject = cycleFixtures.subject();
        ProgramSubject entry = programSubject(program, subject, 4, StatusType.ACTIVE);
        entry.setSubjectRecurrence(SubjectRecurrence.ANNUAL);
        cycle.getTerm().setType(TermType.FIRST_SEMESTER);
        flushAndClear();

        assertThatThrownBy(() -> createSectionOf(subject)).isInstanceOf(SubjectRecurrenceIncompatibleWithTermException.class);
    }

    @Test
    void annualSubjectCanBeOfferedInAnAnnualTerm() {
        Subject subject = cycleFixtures.subject();
        ProgramSubject entry = programSubject(program, subject, 4, StatusType.ACTIVE);
        entry.setSubjectRecurrence(SubjectRecurrence.ANNUAL);
        cycle.getTerm().setType(TermType.ANNUAL);
        flushAndClear();

        assertThat(createSectionOf(subject)).isNotNull();
    }
}
