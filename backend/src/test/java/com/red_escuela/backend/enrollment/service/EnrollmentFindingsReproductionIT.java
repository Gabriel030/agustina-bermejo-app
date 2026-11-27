package com.red_escuela.backend.enrollment.service;

import com.red_escuela.backend.academicstructure.domain.CurricularTerm;
import com.red_escuela.backend.academicstructure.domain.RequirementType;
import com.red_escuela.backend.academicstructure.dto.AddPrerequisiteRequest;
import com.red_escuela.backend.academicstructure.entity.AcademicOfferingCycle;
import com.red_escuela.backend.academicstructure.entity.AcademicOfferingCycleStatus;
import com.red_escuela.backend.academicstructure.entity.Course;
import com.red_escuela.backend.academicstructure.entity.CourseSubject;
import com.red_escuela.backend.academicstructure.entity.CourseSubjectSection;
import com.red_escuela.backend.academicstructure.entity.Program;
import com.red_escuela.backend.academicstructure.entity.ProgramSubject;
import com.red_escuela.backend.academicstructure.entity.Subject;
import com.red_escuela.backend.academicstructure.entity.SubjectSection;
import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.academicstructure.domain.SectionKind;
import com.red_escuela.backend.academicstructure.service.ProgramSubjectService;
import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.enrollment.dto.CreateCourseEnrollmentRequest;
import com.red_escuela.backend.enrollment.dto.CreateSectionEnrollmentRequest;
import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentStatus;
import com.red_escuela.backend.enrollment.entity.SubjectSectionEnrollment;
import com.red_escuela.backend.enrollment.exception.EnrollmentWindowClosedException;
import com.red_escuela.backend.enrollment.exception.ProgramEnrollmentRequiredException;
import com.red_escuela.backend.enrollment.exception.PrerequisiteNotMetException;
import com.red_escuela.backend.enrollment.exception.SectionHasRecordsException;
import com.red_escuela.backend.evaluation.entity.ClosureStatus;
import com.red_escuela.backend.evaluation.entity.GradeItem;
import com.red_escuela.backend.evaluation.entity.GradeItemType;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.shared.domain.Grade;
import com.red_escuela.backend.shared.domain.StatusType;
import com.red_escuela.backend.testsupport.AcademicReproductionFixtures;
import com.red_escuela.backend.testsupport.TermFixtures;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class EnrollmentFindingsReproductionIT extends AbstractIntegrationTest {

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private SubjectSectionEnrollmentService subjectSectionEnrollmentService;

    @Autowired
    private CourseEnrollmentService courseEnrollmentService;

    @Autowired
    private ProgramSubjectService programSubjectService;

    private AcademicReproductionFixtures fixtures;

    @BeforeEach
    void createFixtures() {
        fixtures = new AcademicReproductionFixtures(entityManager);
    }

    private record ProgramSetup(Program program, Subject subject, Term term, SubjectSection section) {
    }

    private ProgramSetup programSetup(String name, Integer capacity) {
        Program program = fixtures.program("Carrera" + name);
        Subject subject = fixtures.subject("Materia" + name);
        fixtures.programSubject(program, subject, 1, CurricularTerm.FIRST);
        Term term = fixtures.openTerm();
        SubjectSection section = fixtures.section(subject, term, null);
        section.setCapacity(capacity);
        entityManager.flush();
        return new ProgramSetup(program, subject, term, section);
    }

    private SubjectSectionEnrollment seatedEnrollment(ProgramSetup setup, String dni, EnrollmentStatus status,
                                                      ProgramEnrollmentStatus programStatus) {
        Student student = fixtures.student(dni);
        ProgramEnrollment programEnrollment = fixtures.programEnrollment(student, setup.program(), programStatus);
        SubjectSectionEnrollment enrollment = fixtures.sectionEnrollment(student, programEnrollment, setup.section(), status);
        entityManager.flush();
        return enrollment;
    }

    @Test
    @Disabled("E34-A-S3")
    void h3EnrollingIsRejectedWhileTheSectionClosureIsClosed() {
        ProgramSetup setup = programSetup("H3", null);
        Student student = fixtures.student("77000001");
        ProgramEnrollment programEnrollment = fixtures.programEnrollment(student, setup.program(), ProgramEnrollmentStatus.ENROLLED);
        fixtures.closure(SectionKind.PROGRAM, setup.section().getId(), ClosureStatus.CLOSED, student.getPerson().getId());
        entityManager.flush();

        assertThatThrownBy(() -> subjectSectionEnrollmentService.enroll(
                programEnrollment.getId(), new CreateSectionEnrollmentRequest(setup.section().getId())))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    @Disabled("E34-A-S5")
    void h13PausedProgramEnrollmentCannotEnrollInASection() {
        ProgramSetup setup = programSetup("H13Pausa", null);
        Student student = fixtures.student("77000003");
        ProgramEnrollment programEnrollment = fixtures.programEnrollment(student, setup.program(), ProgramEnrollmentStatus.PAUSED);
        entityManager.flush();

        assertThatThrownBy(() -> subjectSectionEnrollmentService.enroll(
                programEnrollment.getId(), new CreateSectionEnrollmentRequest(setup.section().getId())))
                .isInstanceOf(ProgramEnrollmentRequiredException.class);
    }

    @Test
    @Disabled("E34-A-S5")
    void h13WaitlistPromotionSkipsAStudentWhoseProgramEnrollmentIsPaused() {
        ProgramSetup setup = programSetup("H13Espera", 1);
        SubjectSectionEnrollment seated = seatedEnrollment(setup, "77000005", EnrollmentStatus.ENROLLED, ProgramEnrollmentStatus.ENROLLED);
        SubjectSectionEnrollment waiting = seatedEnrollment(setup, "77000007", EnrollmentStatus.WAITLISTED, ProgramEnrollmentStatus.PAUSED);

        subjectSectionEnrollmentService.withdraw(seated.getId());
        entityManager.flush();
        entityManager.refresh(waiting);

        assertThat(waiting.getStatus()).isEqualTo(EnrollmentStatus.WAITLISTED);
    }

    @Test
    @Disabled("E34-A-S6")
    void h7StudentCannotWithdrawFromASectionThatAlreadyHasGrades() {
        ProgramSetup setup = programSetup("H7Notas", null);
        SubjectSectionEnrollment enrollment = seatedEnrollment(setup, "77000009", EnrollmentStatus.IN_PROGRESS, ProgramEnrollmentStatus.ENROLLED);
        GradeItem item = new GradeItem();
        item.setProgramSubjectSectionEnrollment(enrollment);
        item.setType(GradeItemType.EXAM);
        item.setSequenceNumber(1);
        item.setDate(LocalDateTime.now());
        item.setScore(Grade.of(BigDecimal.valueOf(7.0)));
        entityManager.persist(item);
        entityManager.flush();

        assertThatThrownBy(() -> subjectSectionEnrollmentService.withdraw(enrollment.getId()))
                .isInstanceOf(SectionHasRecordsException.class);
    }

    @Test
    @Disabled("E34-A-S6")
    void h7StudentCannotWithdrawOutsideTheTermEnrollmentWindow() {
        ProgramSetup setup = programSetup("H7Ventana", null);
        setup.term().setEnrollmentStart(LocalDateTime.of(2020, 1, 1, 0, 0));
        setup.term().setEnrollmentEnd(LocalDateTime.of(2020, 2, 1, 0, 0));
        SubjectSectionEnrollment enrollment = seatedEnrollment(setup, "77000011", EnrollmentStatus.IN_PROGRESS, ProgramEnrollmentStatus.ENROLLED);

        assertThatThrownBy(() -> subjectSectionEnrollmentService.withdraw(enrollment.getId()))
                .isInstanceOf(EnrollmentWindowClosedException.class);
    }

    @Test
    void h15RegularInThePrerequisiteDoesNotEnableTheNextSubjectInTheSameTerm() {
        Program program = fixtures.program("CarreraH15");
        Subject first = fixtures.subject("MateriaH15A");
        Subject second = fixtures.subject("MateriaH15B");
        ProgramSubject firstEntry = fixtures.programSubject(program, first, 1, CurricularTerm.FIRST);
        ProgramSubject secondEntry = fixtures.programSubject(program, second, 2, CurricularTerm.FIRST);
        entityManager.flush();
        programSubjectService.addPrerequisite(secondEntry.getId(),
                new AddPrerequisiteRequest(firstEntry.getId(), RequirementType.PASSED_COURSEWORK, null, null));
        Term term = fixtures.openTerm();
        SubjectSection firstSection = fixtures.section(first, term, null);
        SubjectSection secondSection = fixtures.section(second, term, null);
        Student student = fixtures.student("77000013");
        ProgramEnrollment programEnrollment = fixtures.programEnrollment(student, program, ProgramEnrollmentStatus.ENROLLED);
        fixtures.sectionEnrollment(student, programEnrollment, firstSection, EnrollmentStatus.REGULAR);
        entityManager.flush();

        assertThatThrownBy(() -> subjectSectionEnrollmentService.enroll(
                programEnrollment.getId(), new CreateSectionEnrollmentRequest(secondSection.getId())))
                .isInstanceOf(PrerequisiteNotMetException.class);
    }

    @Test
    void h1StudentClosedAsFreeCanEnrollInTheSameSubjectInTheNextTerm() {
        ProgramSetup setup = programSetup("H1Libre", null);
        SubjectSectionEnrollment closedAsFree = seatedEnrollment(setup, "77000021", EnrollmentStatus.FAILED,
                ProgramEnrollmentStatus.ENROLLED);
        closedAsFree.setWithdrawalReason("Libre por inasistencias");
        SubjectSection nextTermSection = fixtures.section(setup.subject(), fixtures.openTerm(), null);
        entityManager.flush();

        SubjectSectionEnrollment retaken = subjectSectionEnrollmentService.enroll(
                closedAsFree.getProgramEnrollment().getId(), new CreateSectionEnrollmentRequest(nextTermSection.getId()));

        assertThat(retaken.getStatus()).isIn(EnrollmentStatus.ENROLLED, EnrollmentStatus.IN_PROGRESS);
    }

    @Test
    void h15RegularInThePrerequisiteFromAnEarlierTermEnablesTheNextSubject() {
        Program program = fixtures.program("CarreraH15Previo");
        Subject first = fixtures.subject("MateriaH15PrevioA");
        Subject second = fixtures.subject("MateriaH15PrevioB");
        ProgramSubject firstEntry = fixtures.programSubject(program, first, 1, CurricularTerm.FIRST);
        ProgramSubject secondEntry = fixtures.programSubject(program, second, 2, CurricularTerm.FIRST);
        entityManager.flush();
        programSubjectService.addPrerequisite(secondEntry.getId(),
                new AddPrerequisiteRequest(firstEntry.getId(), RequirementType.PASSED_COURSEWORK, null, null));
        SubjectSection firstSection = fixtures.section(first, fixtures.openTerm(), null);
        SubjectSection secondSection = fixtures.section(second, fixtures.openTerm(), null);
        Student student = fixtures.student("77000014");
        ProgramEnrollment programEnrollment = fixtures.programEnrollment(student, program, ProgramEnrollmentStatus.ENROLLED);
        fixtures.sectionEnrollment(student, programEnrollment, firstSection, EnrollmentStatus.REGULAR);
        entityManager.flush();

        SubjectSectionEnrollment enrolled = subjectSectionEnrollmentService.enroll(
                programEnrollment.getId(), new CreateSectionEnrollmentRequest(secondSection.getId()));

        assertThat(enrolled.getId()).isNotNull();
    }

    @Test
    @Disabled("E34-A-S5")
    void h6CourseEnrollmentIgnoresModuleSectionsWhoseCycleIsStillDraft() {
        Term term = TermFixtures.newTerm();
        term.setYear(2026);
        term.setStatus(StatusType.ACTIVE);
        term.setStartDate(LocalDateTime.now().plusMonths(1));
        entityManager.persist(term);
        Course course = new Course();
        course.setName("Curso H6 " + System.nanoTime());
        course.setTerm(term);
        course.setIsActive(StatusType.ACTIVE);
        entityManager.persist(course);
        CourseSubject module = new CourseSubject();
        module.setCourse(course);
        module.setName("Modulo H6");
        entityManager.persist(module);
        Student student = fixtures.student("77000015");
        AcademicOfferingCycle cycle = new AcademicOfferingCycle();
        cycle.setTerm(term);
        cycle.setCourse(course);
        cycle.setStatus(AcademicOfferingCycleStatus.DRAFT);
        cycle.setCreatedBy(student.getPerson());
        cycle.setCreatedAt(LocalDateTime.now());
        entityManager.persist(cycle);
        CourseSubjectSection section = new CourseSubjectSection();
        section.setName("Comision H6");
        section.setCourseSubject(module);
        section.setTerm(term);
        section.setStatus(StatusType.ACTIVE);
        section.setAcademicOfferingCycle(cycle);
        entityManager.persist(section);
        entityManager.flush();

        assertThatThrownBy(() -> courseEnrollmentService.enroll(student.getId(), new CreateCourseEnrollmentRequest(course.getId(), null)))
                .isInstanceOf(RuntimeException.class);
    }
}
