package com.red_escuela.backend.enrollment.service;

import com.red_escuela.backend.academicstructure.domain.CurricularTerm;
import com.red_escuela.backend.academicstructure.entity.Program;
import com.red_escuela.backend.academicstructure.entity.ProgramSubject;
import com.red_escuela.backend.academicstructure.entity.Subject;
import com.red_escuela.backend.academicstructure.entity.SubjectSection;
import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.config.TestSubjectCodes;
import com.red_escuela.backend.enrollment.dto.ChangeSectionRequest;
import com.red_escuela.backend.enrollment.dto.CreateSectionEnrollmentRequest;
import com.red_escuela.backend.enrollment.entity.DirectorApprovalRequest;
import com.red_escuela.backend.enrollment.entity.DirectorApprovalRequestStatus;
import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.enrollment.entity.ExceptionType;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentStatus;
import com.red_escuela.backend.enrollment.entity.SubjectSectionEnrollment;
import com.red_escuela.backend.enrollment.exception.SubjectAlreadyApprovedException;
import com.red_escuela.backend.enrollment.exception.SubjectAlreadyRegularException;
import com.red_escuela.backend.enrollment.repository.SubjectSectionEnrollmentRepository;
import com.red_escuela.backend.identity.entity.Institution;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.identity.entity.StudentOnboardingStatus;
import com.red_escuela.backend.shared.domain.Address;
import com.red_escuela.backend.shared.domain.StatusType;
import com.red_escuela.backend.testsupport.TermFixtures;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class SubjectRetakeRulesTest extends AbstractIntegrationTest {

    @Autowired
    private SubjectSectionEnrollmentService enrollmentService;

    @Autowired
    private SubjectSectionEnrollmentRepository enrollmentRepository;

    @Autowired
    private EntityManager entityManager;

    private record World(Student student, Program program, Subject subject, ProgramEnrollment programEnrollment) {
    }

    private World world(String dni) {
        Institution institution = new Institution();
        institution.setName("Instituto " + dni);
        entityManager.persist(institution);
        Student student = student(dni, institution);
        Program program = new Program();
        program.setName("Carrera " + dni);
        program.setTotalYears(3);
        program.setTotalCreditsRequired(100);
        entityManager.persist(program);
        Subject subject = new Subject();
        subject.setCode(TestSubjectCodes.next());
        subject.setName("Materia " + dni);
        entityManager.persist(subject);
        ProgramSubject link = new ProgramSubject();
        link.setProgram(program);
        link.setSubject(subject);
        link.setCredits(10);
        link.setCurricularYear(1);
        link.setCurricularTerm(CurricularTerm.FIRST);
        link.setStatus(StatusType.ACTIVE);
        entityManager.persist(link);
        ProgramEnrollment programEnrollment = programEnrollment(student, program);
        return new World(student, program, subject, programEnrollment);
    }

    private Student student(String dni, Institution institution) {
        Address address = new Address();
        entityManager.persist(address);
        Person person = new Person();
        person.setName("Alumno");
        person.setLastname(dni);
        person.setDni(dni);
        person.setUsername(dni);
        person.setEmail(dni + "@example.com");
        person.setAddress(address);
        person.setStatus(StatusType.ACTIVE);
        entityManager.persist(person);
        Student student = new Student();
        student.setPerson(person);
        student.setInstitution(institution);
        student.setOnboardingStatus(StudentOnboardingStatus.CONFIRMADO);
        entityManager.persist(student);
        return student;
    }

    private ProgramEnrollment programEnrollment(Student student, Program program) {
        ProgramEnrollment enrollment = new ProgramEnrollment();
        enrollment.setStudent(student);
        enrollment.setProgram(program);
        enrollment.setStatus(ProgramEnrollmentStatus.ENROLLED);
        enrollment.setEnrollmentDate(LocalDateTime.now());
        entityManager.persist(enrollment);
        return enrollment;
    }

    private Term term() {
        Term term = TermFixtures.newTerm();
        term.setYear(2026);
        term.setDescription("2026-1C");
        term.setStatus(StatusType.ACTIVE);
        term.setEnrollmentStart(TermFixtures.OPEN_WINDOW_START);
        term.setEnrollmentEnd(TermFixtures.OPEN_WINDOW_END);
        term.setStartDate(term.getEnrollmentStart());
        term.setEndDate(term.getEnrollmentEnd());
        entityManager.persist(term);
        return term;
    }

    private SubjectSection section(Subject subject, Term term, Integer capacity, String suffix) {
        SubjectSection section = new SubjectSection();
        section.setName(subject.getName() + " - " + suffix);
        section.setSubject(subject);
        section.setTerm(term);
        section.setCapacity(capacity);
        section.setStatus(StatusType.ACTIVE);
        entityManager.persist(section);
        return section;
    }

    private SubjectSectionEnrollment row(World world, SubjectSection section, EnrollmentStatus status, LocalDateTime enrolledAt) {
        SubjectSectionEnrollment row = new SubjectSectionEnrollment();
        row.setStudent(world.student());
        row.setProgramEnrollment(world.programEnrollment());
        row.setSubjectSection(section);
        row.setStatus(status);
        row.setEnrolledAt(enrolledAt);
        row.setAttemptNumber(1);
        entityManager.persist(row);
        return row;
    }

    private long rowsOfSubject(World world) {
        entityManager.flush();
        return enrollmentRepository.findByProgramEnrollmentIdAndSubjectSection_Subject_IdAndStatusIn(
                world.programEnrollment().getId(), world.subject().getId(), List.of(EnrollmentStatus.values())).size();
    }

    @Test
    void enrollingInASubjectAlreadyApprovedIsRejectedAndCreatesNoRow() {
        World world = world("81000001");
        SubjectSection passed = section(world.subject(), term(), null, "Com A");
        SubjectSection another = section(world.subject(), term(), null, "Com B");
        row(world, passed, EnrollmentStatus.APPROVED, LocalDateTime.now().minusYears(1));
        long before = rowsOfSubject(world);

        assertThatThrownBy(() -> enrollmentService.enroll(
                world.programEnrollment().getId(), new CreateSectionEnrollmentRequest(another.getId())))
                .isInstanceOf(SubjectAlreadyApprovedException.class);

        assertThat(rowsOfSubject(world)).isEqualTo(before);
    }

    @Test
    void enrollingInTheSameSectionOfAnApprovedSubjectIsRejectedAsAlreadyApproved() {
        World world = world("81000002");
        SubjectSection passed = section(world.subject(), term(), null, "Com A");
        row(world, passed, EnrollmentStatus.APPROVED, LocalDateTime.now().minusYears(1));
        entityManager.flush();

        assertThatThrownBy(() -> enrollmentService.enroll(
                world.programEnrollment().getId(), new CreateSectionEnrollmentRequest(passed.getId())))
                .isInstanceOf(SubjectAlreadyApprovedException.class);
    }

    @Test
    void enrollingInASubjectWithRegularStatusIsRejected() {
        World world = world("81000011");
        SubjectSection passed = section(world.subject(), term(), null, "Com A");
        SubjectSection another = section(world.subject(), term(), null, "Com B");
        row(world, passed, EnrollmentStatus.REGULAR, LocalDateTime.now().minusMonths(6));
        long before = rowsOfSubject(world);

        assertThatThrownBy(() -> enrollmentService.enroll(
                world.programEnrollment().getId(), new CreateSectionEnrollmentRequest(another.getId())))
                .isInstanceOf(SubjectAlreadyRegularException.class);

        assertThat(rowsOfSubject(world)).isEqualTo(before);
    }

    @Test
    void approvedBeatsRegularWhenBothExistInTheSameSubject() {
        World world = world("81000012");
        SubjectSection first = section(world.subject(), term(), null, "Com A");
        SubjectSection second = section(world.subject(), term(), null, "Com B");
        SubjectSection third = section(world.subject(), term(), null, "Com C");
        row(world, first, EnrollmentStatus.REGULAR, LocalDateTime.now().minusMonths(8));
        row(world, second, EnrollmentStatus.APPROVED, LocalDateTime.now().minusMonths(4));
        entityManager.flush();

        assertThatThrownBy(() -> enrollmentService.enroll(
                world.programEnrollment().getId(), new CreateSectionEnrollmentRequest(third.getId())))
                .isInstanceOf(SubjectAlreadyApprovedException.class);
    }

    @Test
    void prerequisiteWaiverDoesNotAllowRetakingAnApprovedSubject() {
        World world = world("81000021");
        SubjectSection passed = section(world.subject(), term(), null, "Com A");
        SubjectSection another = section(world.subject(), term(), null, "Com B");
        row(world, passed, EnrollmentStatus.APPROVED, LocalDateTime.now().minusYears(1));
        DirectorApprovalRequest waiver = new DirectorApprovalRequest();
        waiver.setType(ExceptionType.PREREQUISITE_WAIVER);
        waiver.setProgramEnrollment(world.programEnrollment());
        waiver.setSubject(world.subject());
        waiver.setJustification("Excepcion de correlatividad");
        waiver.setStatus(DirectorApprovalRequestStatus.APPROVED);
        waiver.setRequestedBy(world.student().getPerson());
        waiver.setRequestedAt(LocalDateTime.now());
        entityManager.persist(waiver);
        entityManager.flush();

        assertThatThrownBy(() -> enrollmentService.enroll(
                world.programEnrollment().getId(), new CreateSectionEnrollmentRequest(another.getId())))
                .isInstanceOf(SubjectAlreadyApprovedException.class);
    }

    @Test
    void failedSubjectCanBeRetakenWithTheNextAttemptNumber() {
        World world = world("81000031");
        SubjectSection failed = section(world.subject(), term(), null, "Com A");
        SubjectSection retake = section(world.subject(), term(), null, "Com B");
        row(world, failed, EnrollmentStatus.FAILED, LocalDateTime.now().minusYears(1));
        entityManager.flush();

        SubjectSectionEnrollment result = enrollmentService.enroll(
                world.programEnrollment().getId(), new CreateSectionEnrollmentRequest(retake.getId()));

        assertThat(result.getStatus()).isEqualTo(EnrollmentStatus.ENROLLED);
        assertThat(result.getAttemptNumber()).isEqualTo(2);
    }

    @Test
    void withdrawnSubjectCanBeEnrolledAgain() {
        World world = world("81000032");
        Term term = term();
        SubjectSection withdrawn = section(world.subject(), term, null, "Com A");
        row(world, withdrawn, EnrollmentStatus.WITHDRAWN, LocalDateTime.now().minusMonths(2));
        entityManager.flush();

        SubjectSectionEnrollment result = enrollmentService.enroll(
                world.programEnrollment().getId(), new CreateSectionEnrollmentRequest(withdrawn.getId()));

        assertThat(result.getStatus()).isEqualTo(EnrollmentStatus.ENROLLED);
    }

    @Test
    void changingSectionIsRejectedWhenTheSubjectIsAlreadyApproved() {
        World world = world("81000041");
        Term term = term();
        SubjectSection origin = section(world.subject(), term, null, "Com A");
        SubjectSection destination = section(world.subject(), term, null, "Com B");
        SubjectSection passed = section(world.subject(), term(), null, "Com C");
        SubjectSectionEnrollment current = row(world, origin, EnrollmentStatus.ENROLLED, LocalDateTime.now());
        row(world, passed, EnrollmentStatus.APPROVED, LocalDateTime.now().minusYears(1));
        entityManager.flush();

        assertThatThrownBy(() -> enrollmentService.changeSection(
                current.getId(), new ChangeSectionRequest(destination.getId())))
                .isInstanceOf(SubjectAlreadyApprovedException.class);

        assertThat(enrollmentService.get(current.getId()).getStatus()).isEqualTo(EnrollmentStatus.ENROLLED);
    }

    @Test
    void changingSectionIsRejectedWhenTheSubjectIsAlreadyRegular() {
        World world = world("81000042");
        Term term = term();
        SubjectSection origin = section(world.subject(), term, null, "Com A");
        SubjectSection destination = section(world.subject(), term, null, "Com B");
        SubjectSection passed = section(world.subject(), term(), null, "Com C");
        SubjectSectionEnrollment current = row(world, origin, EnrollmentStatus.ENROLLED, LocalDateTime.now());
        row(world, passed, EnrollmentStatus.REGULAR, LocalDateTime.now().minusMonths(6));
        entityManager.flush();

        assertThatThrownBy(() -> enrollmentService.changeSection(
                current.getId(), new ChangeSectionRequest(destination.getId())))
                .isInstanceOf(SubjectAlreadyRegularException.class);
    }

    @Test
    void changingSectionWithoutAPassedRowStillWorks() {
        World world = world("81000043");
        Term term = term();
        SubjectSection origin = section(world.subject(), term, null, "Com A");
        SubjectSection destination = section(world.subject(), term, null, "Com B");
        SubjectSectionEnrollment current = row(world, origin, EnrollmentStatus.ENROLLED, LocalDateTime.now());
        entityManager.flush();

        SubjectSectionEnrollment moved = enrollmentService.changeSection(
                current.getId(), new ChangeSectionRequest(destination.getId()));

        assertThat(moved.getSubjectSection().getId()).isEqualTo(destination.getId());
    }

    @Test
    void waitlistPromotionSkipsAStudentWhoAlreadyPassedTheSubject() {
        World seated = world("81000051");
        Term term = term();
        SubjectSection contested = section(seated.subject(), term, 1, "Com A");
        SubjectSectionEnrollment holder = row(seated, contested, EnrollmentStatus.ENROLLED, LocalDateTime.now().minusDays(3));

        World blocked = worldInSameSubject("81000052", seated);
        SubjectSection passed = section(seated.subject(), term(), null, "Com Vieja");
        row(blocked, passed, EnrollmentStatus.APPROVED, LocalDateTime.now().minusYears(1));
        SubjectSectionEnrollment blockedWaiting = row(blocked, contested, EnrollmentStatus.WAITLISTED, LocalDateTime.now().minusDays(2));

        World eligible = worldInSameSubject("81000053", seated);
        SubjectSectionEnrollment eligibleWaiting = row(eligible, contested, EnrollmentStatus.WAITLISTED, LocalDateTime.now().minusDays(1));
        entityManager.flush();

        enrollmentService.withdraw(holder.getId());
        entityManager.flush();
        entityManager.clear();

        assertThat(enrollmentService.get(eligibleWaiting.getId()).getStatus()).isEqualTo(EnrollmentStatus.ENROLLED);
        assertThat(enrollmentService.get(blockedWaiting.getId()).getStatus()).isEqualTo(EnrollmentStatus.WAITLISTED);
    }

    @Test
    void waitlistPromotionPromotesTheOldestWhenNobodyHasPassedTheSubject() {
        World seated = world("81000061");
        Term term = term();
        SubjectSection contested = section(seated.subject(), term, 1, "Com A");
        SubjectSectionEnrollment holder = row(seated, contested, EnrollmentStatus.ENROLLED, LocalDateTime.now().minusDays(3));
        World oldest = worldInSameSubject("81000062", seated);
        SubjectSectionEnrollment oldestWaiting = row(oldest, contested, EnrollmentStatus.WAITLISTED, LocalDateTime.now().minusDays(2));
        World newest = worldInSameSubject("81000063", seated);
        SubjectSectionEnrollment newestWaiting = row(newest, contested, EnrollmentStatus.WAITLISTED, LocalDateTime.now().minusDays(1));
        entityManager.flush();

        enrollmentService.withdraw(holder.getId());
        entityManager.flush();
        entityManager.clear();

        assertThat(enrollmentService.get(oldestWaiting.getId()).getStatus()).isEqualTo(EnrollmentStatus.ENROLLED);
        assertThat(enrollmentService.get(newestWaiting.getId()).getStatus()).isEqualTo(EnrollmentStatus.WAITLISTED);
    }

    private World worldInSameSubject(String dni, World base) {
        Student student = student(dni, base.student().getInstitution());
        return new World(student, base.program(), base.subject(), programEnrollment(student, base.program()));
    }
}
