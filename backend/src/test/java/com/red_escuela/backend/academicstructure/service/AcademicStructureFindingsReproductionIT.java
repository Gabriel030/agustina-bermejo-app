package com.red_escuela.backend.academicstructure.service;

import com.red_escuela.backend.academicstructure.api.SectionFinder;
import com.red_escuela.backend.academicstructure.domain.CurricularTerm;
import com.red_escuela.backend.academicstructure.domain.RequirementType;
import com.red_escuela.backend.academicstructure.domain.TermType;
import com.red_escuela.backend.academicstructure.dto.AddPrerequisiteRequest;
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
import com.red_escuela.backend.academicstructure.exception.CycleAlreadyInProgressException;
import com.red_escuela.backend.academicstructure.exception.SectionLastInstructorRemovalException;
import com.red_escuela.backend.academicstructure.exception.SectionNotOpenForEnrollmentException;
import com.red_escuela.backend.academicstructure.exception.TermNotOpenForOfferingException;
import jakarta.persistence.PersistenceException;
import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.shared.domain.StatusType;
import com.red_escuela.backend.testsupport.AcademicReproductionFixtures;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
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

    @Autowired
    private CreateAcademicOfferingCycleTransaction createCycleTransaction;

    @Autowired
    private CreateCycleSectionTransaction createCycleSectionTransaction;

    @Autowired
    private CycleApproval cycleApproval;

    private AcademicReproductionFixtures fixtures;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

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
    void sectionOfADraftCycleIsNotOpenForEnrollment() {
        Person creator = fixtures.student("75000010").getPerson();
        Subject subject = fixtures.subject("MateriaH4");
        Term term = fixtures.openTerm();
        Program program = fixtures.program("CarreraH4");
        SubjectSection section = fixtures.section(subject, term, null);
        section.setAcademicOfferingCycle(cycle(term, program, AcademicOfferingCycleStatus.DRAFT, creator));
        entityManager.flush();

        assertThatThrownBy(() -> sectionFinder.findOpenProgramSection(section.getId()))
                .isInstanceOf(SectionNotOpenForEnrollmentException.class);
    }

    @Test
    void cycleCannotBeCreatedInAnInactiveTerm() {
        Program program = fixtures.program("CarreraH11Inactiva");
        Term term = termWith(StatusType.INACTIVE, LocalDateTime.of(2026, 3, 1, 0, 0), LocalDateTime.of(2999, 7, 1, 0, 0));

        assertThatThrownBy(() -> createCycleTransaction.create(program.getId(), null, term.getId()))
                .isInstanceOf(TermNotOpenForOfferingException.class);
    }

    @Test
    void cycleCannotBeCreatedInATermThatAlreadyEnded() {
        Program program = fixtures.program("CarreraH11Vencida");
        Term term = termWith(StatusType.ACTIVE, LocalDateTime.of(2020, 3, 1, 0, 0), LocalDateTime.of(2020, 7, 1, 0, 0));

        assertThatThrownBy(() -> createCycleTransaction.create(program.getId(), null, term.getId()))
                .isInstanceOf(TermNotOpenForOfferingException.class);
    }

    @Test
    void sectionCannotBeCreatedInTheCycleOfATermThatAlreadyEnded() {
        Person creator = fixtures.student("75000011").getPerson();
        Subject subject = fixtures.subject("MateriaH11Vencida");
        Term term = termWith(StatusType.ACTIVE, LocalDateTime.of(2020, 3, 1, 0, 0), LocalDateTime.of(2020, 7, 1, 0, 0));
        AcademicOfferingCycle draft = cycle(term, fixtures.program("CarreraH11Seccion"), AcademicOfferingCycleStatus.DRAFT, creator);

        assertThatThrownBy(() -> createCycleSectionTransaction.createSection(draft.getId(), "Comision", subject.getId(), null, 30,
                null, null, null, null, LocalDateTime.now())).isInstanceOf(TermNotOpenForOfferingException.class);
    }

    @Test
    void cycleOfATermThatAlreadyEndedCannotBeApproved() {
        Person creator = fixtures.student("75000012").getPerson();
        Term term = termWith(StatusType.ACTIVE, LocalDateTime.of(2020, 3, 1, 0, 0), LocalDateTime.of(2020, 7, 1, 0, 0));
        AcademicOfferingCycle pending = cycle(term, fixtures.program("CarreraH11Aprobar"), AcademicOfferingCycleStatus.PENDING_APPROVAL, creator);

        assertThatThrownBy(() -> cycleApproval.approve(pending.getId(), null)).isInstanceOf(TermNotOpenForOfferingException.class);
    }

    @Test
    void secondCycleOfTheSameProgramAndTermIsRejectedWhileTheFirstIsApproved() {
        Person creator = fixtures.student("75000013").getPerson();
        Term term = fixtures.openTerm();
        Program program = fixtures.program("CarreraH11Duplicado");
        cycle(term, program, AcademicOfferingCycleStatus.APPROVED, creator);
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(creator.getId().toString(), null));

        assertThatThrownBy(() -> createCycleTransaction.create(program.getId(), null, term.getId()))
                .isInstanceOf(CycleAlreadyInProgressException.class);
    }

    @Test
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
    void twoLiveCyclesOfTheSameProgramAndTermAreRejectedByTheDatabase() {
        Person creator = fixtures.student("75000002").getPerson();
        Term term = fixtures.openTerm();
        Program program = fixtures.program("CarreraH11Ciclos");
        cycle(term, program, AcademicOfferingCycleStatus.APPROVED, creator);

        assertThatThrownBy(() -> cycle(term, program, AcademicOfferingCycleStatus.APPROVED, creator))
                .isInstanceOf(PersistenceException.class);
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
    void h18RemovingTheOnlyInstructorOfAnActiveSectionIsRejected() {
        Instructor instructor = fixtures.instructor("75000003");
        SubjectSection section = fixtures.section(fixtures.subject("MateriaH18"), fixtures.openTerm(), null);
        fixtures.assignInstructor(section, instructor);
        entityManager.flush();
        SubjectSectionInstructor assignment = subjectSectionService.listInstructors(section.getId()).get(0);

        assertThatThrownBy(() -> subjectSectionService.removeInstructor(section.getId(), assignment.getId()))
                .isInstanceOf(SectionLastInstructorRemovalException.class);
    }
}
