package com.red_escuela.backend.academicstructure.service;

import com.red_escuela.backend.academicstructure.api.SectionFinder;
import com.red_escuela.backend.academicstructure.domain.CurricularTerm;
import com.red_escuela.backend.academicstructure.domain.RequirementType;
import com.red_escuela.backend.academicstructure.domain.TermType;
import com.red_escuela.backend.academicstructure.dto.AddPrerequisiteRequest;
import com.red_escuela.backend.academicstructure.dto.CreateSectionRequest;
import com.red_escuela.backend.academicstructure.dto.CreateTermRequest;
import com.red_escuela.backend.academicstructure.dto.UpdateProgramSubjectRequest;
import com.red_escuela.backend.academicstructure.entity.AcademicOfferingCycle;
import com.red_escuela.backend.academicstructure.entity.AcademicOfferingCycleStatus;
import com.red_escuela.backend.academicstructure.entity.Instructor;
import com.red_escuela.backend.academicstructure.entity.Program;
import com.red_escuela.backend.academicstructure.entity.ProgramSubject;
import com.red_escuela.backend.academicstructure.entity.Subject;
import com.red_escuela.backend.academicstructure.entity.SubjectSection;
import com.red_escuela.backend.academicstructure.entity.SubjectSectionInstructor;
import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.academicstructure.exception.SectionNotOpenForEnrollmentException;
import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.shared.domain.StatusType;
import com.red_escuela.backend.testsupport.AcademicReproductionFixtures;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AcademicStructureFindingsReproductionIT extends AbstractIntegrationTest {

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private SubjectSectionService subjectSectionService;

    @Autowired
    private ProgramSubjectService programSubjectService;

    @Autowired
    private TermCreation termCreation;

    @Autowired
    private SectionFinder sectionFinder;

    private AcademicReproductionFixtures fixtures;

    @BeforeEach
    void createFixtures() {
        fixtures = new AcademicReproductionFixtures(entityManager);
    }

    private Term termWith(StatusType status, LocalDateTime start, LocalDateTime end) {
        Term term = fixtures.openTerm();
        term.setStatus(status);
        term.setStartDate(start);
        term.setEndDate(end);
        entityManager.flush();
        return term;
    }

    private AcademicOfferingCycle cycle(Term term, Program program, AcademicOfferingCycleStatus status, Person creator) {
        AcademicOfferingCycle cycle = new AcademicOfferingCycle();
        cycle.setTerm(term);
        cycle.setProgram(program);
        cycle.setStatus(status);
        cycle.setCreatedBy(creator);
        cycle.setCreatedAt(LocalDateTime.now());
        entityManager.persist(cycle);
        entityManager.flush();
        return cycle;
    }

    private ProgramSubject programSubject(Program program, Subject subject, int year, CurricularTerm curricularTerm) {
        ProgramSubject programSubject = new ProgramSubject();
        programSubject.setProgram(program);
        programSubject.setPlan(com.red_escuela.backend.testsupport.TestCurriculumPlans.planOf(program));
        programSubject.setSubject(subject);
        programSubject.setCredits(10);
        programSubject.setCurricularYear(year);
        programSubject.setCurricularTerm(curricularTerm);
        programSubject.setStatus(StatusType.ACTIVE);
        entityManager.persist(programSubject);
        return programSubject;
    }

    @Test
    @Disabled("E34-A-S5")
    void h4SectionCreatedThroughTheApiIsNotApprovedWithoutAnOfferingCycle() {
        Subject subject = fixtures.subject("MateriaH4");
        Term term = fixtures.openTerm();
        entityManager.flush();

        SubjectSection created = subjectSectionService.create(subject.getId(),
                new CreateSectionRequest("Comision H4", term.getId(), null, 30, null));

        assertThat(created.isOfferingApproved()).isFalse();
    }

    @Test
    @Disabled("E34-A-S5")
    void h11SectionCannotBeCreatedInAnInactiveTerm() {
        Subject subject = fixtures.subject("MateriaH11Inactiva");
        Term term = termWith(StatusType.INACTIVE, LocalDateTime.of(2026, 3, 1, 0, 0), LocalDateTime.of(2026, 7, 1, 0, 0));

        assertThatThrownBy(() -> subjectSectionService.create(subject.getId(),
                new CreateSectionRequest("Comision H11", term.getId(), null, 30, null))).isInstanceOf(RuntimeException.class);
    }

    @Test
    @Disabled("E34-A-S5")
    void h11SectionCannotBeCreatedInATermThatAlreadyEnded() {
        Subject subject = fixtures.subject("MateriaH11Vencido");
        Term term = termWith(StatusType.ACTIVE, LocalDateTime.of(2020, 3, 1, 0, 0), LocalDateTime.of(2020, 7, 1, 0, 0));

        assertThatThrownBy(() -> subjectSectionService.create(subject.getId(),
                new CreateSectionRequest("Comision H11 vencida", term.getId(), null, 30, null))).isInstanceOf(RuntimeException.class);
    }

    @Test
    @Disabled("E34-A-S5")
    void h8SectionOfAClosedCycleIsNotOpenForEnrollment() {
        Person creator = fixtures.student("75000001").getPerson();
        Subject subject = fixtures.subject("MateriaH8");
        Term term = fixtures.openTerm();
        Program program = fixtures.program("CarreraH8");
        SubjectSection section = fixtures.section(subject, term, null);
        section.setAcademicOfferingCycle(cycle(term, program, AcademicOfferingCycleStatus.CLOSED, creator));
        entityManager.flush();

        assertThatThrownBy(() -> sectionFinder.findOpenProgramSection(section.getId()))
                .isInstanceOf(SectionNotOpenForEnrollmentException.class);
    }

    @Test
    @Disabled("E34-A-S5")
    void h11TwoApprovedCyclesOfTheSameProgramAndTermAreRejected() {
        Person creator = fixtures.student("75000002").getPerson();
        Term term = fixtures.openTerm();
        Program program = fixtures.program("CarreraH11Ciclos");
        cycle(term, program, AcademicOfferingCycleStatus.APPROVED, creator);

        assertThatThrownBy(() -> cycle(term, program, AcademicOfferingCycleStatus.APPROVED, creator))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    @Disabled("E34-A-S5")
    void h11TermCreationRejectsAYearThatDoesNotMatchItsDates() {
        CreateTermRequest request = new CreateTermRequest(2026, "2026-1C desfasado", TermType.FIRST_SEMESTER,
                LocalDateTime.of(2031, 3, 1, 0, 0), LocalDateTime.of(2031, 7, 1, 0, 0),
                LocalDateTime.of(2031, 2, 1, 0, 0), LocalDateTime.of(2031, 3, 15, 0, 0));

        assertThatThrownBy(() -> termCreation.create(request)).isInstanceOf(RuntimeException.class);
    }

    @Test
    @Disabled("E34-A-S5")
    void h11TermCreationRejectsAnEnrollmentWindowThatEndsAfterTheTermEnds() {
        CreateTermRequest request = new CreateTermRequest(2026, "2026-1C ventana tardia", TermType.FIRST_SEMESTER,
                LocalDateTime.of(2026, 3, 1, 0, 0), LocalDateTime.of(2026, 7, 1, 0, 0),
                LocalDateTime.of(2026, 2, 1, 0, 0), LocalDateTime.of(2026, 12, 15, 0, 0));

        assertThatThrownBy(() -> termCreation.create(request)).isInstanceOf(RuntimeException.class);
    }

    @Test
    @Disabled("E34-A-S5")
    void h14UpdateRejectsACurricularPlacementThatBreaksAnExistingPrerequisiteOrder() {
        Program program = fixtures.program("CarreraH14");
        ProgramSubject first = programSubject(program, fixtures.subject("MateriaH14A"), 1, CurricularTerm.FIRST);
        ProgramSubject second = programSubject(program, fixtures.subject("MateriaH14B"), 2, CurricularTerm.FIRST);
        entityManager.flush();
        programSubjectService.addPrerequisite(second.getId(),
                new AddPrerequisiteRequest(first.getId(), RequirementType.APPROVED, null, null));

        assertThatThrownBy(() -> programSubjectService.update(first.getId(),
                new UpdateProgramSubjectRequest(2, CurricularTerm.SECOND, null, null, null, null)))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    @Disabled("E34-A-S5")
    void h14DeactivatingASubjectThatIsAPrerequisiteOfAnActiveOneIsRejected() {
        Program program = fixtures.program("CarreraH14Baja");
        ProgramSubject first = programSubject(program, fixtures.subject("MateriaH14C"), 1, CurricularTerm.FIRST);
        ProgramSubject second = programSubject(program, fixtures.subject("MateriaH14D"), 2, CurricularTerm.FIRST);
        entityManager.flush();
        programSubjectService.addPrerequisite(second.getId(),
                new AddPrerequisiteRequest(first.getId(), RequirementType.APPROVED, null, null));

        assertThatThrownBy(() -> programSubjectService.deactivate(first.getId())).isInstanceOf(RuntimeException.class);
    }

    @Test
    @Disabled("E34-A-S5")
    void h18RemovingTheOnlyInstructorOfAnActiveSectionIsRejected() {
        Instructor instructor = fixtures.instructor("75000003");
        SubjectSection section = fixtures.section(fixtures.subject("MateriaH18"), fixtures.openTerm(), null);
        fixtures.assignInstructor(section, instructor);
        entityManager.flush();
        SubjectSectionInstructor assignment = subjectSectionService.listInstructors(section.getId()).get(0);

        assertThatThrownBy(() -> subjectSectionService.removeInstructor(section.getId(), assignment.getId()))
                .isInstanceOf(RuntimeException.class);
    }
}
