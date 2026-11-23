package com.red_escuela.backend.enrollment;

import com.red_escuela.backend.academicstructure.controller.SubjectSectionController;
import com.red_escuela.backend.academicstructure.domain.InstructorRole;
import com.red_escuela.backend.academicstructure.dto.SectionInstructorResponse;
import com.red_escuela.backend.academicstructure.dto.SubjectSectionResponse;
import com.red_escuela.backend.academicstructure.entity.Instructor;
import com.red_escuela.backend.academicstructure.entity.Program;
import com.red_escuela.backend.academicstructure.entity.Subject;
import com.red_escuela.backend.academicstructure.entity.SubjectSection;
import com.red_escuela.backend.academicstructure.entity.SubjectSectionInstructor;
import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.config.TestSubjectCodes;
import com.red_escuela.backend.enrollment.controller.ProgramEnrollmentController;
import com.red_escuela.backend.enrollment.dto.ProgramEnrollmentResponse;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentStatus;
import com.red_escuela.backend.identity.entity.Institution;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.identity.entity.StudentOnboardingStatus;
import com.red_escuela.backend.notification.dto.NotificationResponse;
import com.red_escuela.backend.notification.entity.Notification;
import com.red_escuela.backend.notification.entity.NotificationType;
import com.red_escuela.backend.notification.usecase.inbox.ListMyNotificationsUseCase;
import com.red_escuela.backend.shared.domain.Address;
import com.red_escuela.backend.shared.domain.Percentage;
import com.red_escuela.backend.shared.domain.StatusType;
import com.red_escuela.backend.shared.pagination.PageResponse;
import com.red_escuela.backend.shared.permission.PermissionSnapshotCache;
import com.red_escuela.backend.shared.persistence.QueryCountAssertions;
import com.red_escuela.backend.testsupport.TermFixtures;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class FrequentReadsQueryCountTest extends AbstractIntegrationTest {
    private static final int MANY = 50;
    private static final int PAST_BATCH = 120;
    private static final int MAX_NOTIFICATIONS_PAGE_SIZE = 100;
    private static final int PROGRAM_ENROLLMENTS_PAST_BATCH_QUERIES = 4;
    private static final int SECTIONS_PAST_BATCH_QUERIES = 2;
    private static final int INSTRUCTORS_PAST_BATCH_QUERIES = 7;
    private static final int NOTIFICATIONS_PAST_BATCH_QUERIES = 2;
    private static final int PROGRAM_ENROLLMENTS_BY_STUDENT_QUERIES = 2;
    private static final int SECTIONS_BY_SUBJECT_QUERIES = 2;
    private static final int SECTION_INSTRUCTORS_QUERIES = 3;
    private static final int NOTIFICATIONS_QUERIES = 1;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Autowired
    private ProgramEnrollmentController programEnrollmentController;

    @Autowired
    private SubjectSectionController subjectSectionController;

    @Autowired
    private ListMyNotificationsUseCase listMyNotificationsUseCase;

    @Autowired
    private PermissionSnapshotCache permissionSnapshotCache;

    private int sequence = 71000000;

    @BeforeEach
    void warmPermissionSnapshot() {
        permissionSnapshotCache.current();
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void programEnrollmentsByStudentQueryCountDoesNotGrowWithEnrollments() {
        Student student = persistStudent(persistInstitution());
        for (int i = 0; i < MANY; i++) {
            Program program = persistProgram("Program " + i);
            persistProgramEnrollment(student, program);
        }
        entityManager.flush();
        entityManager.clear();
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken("director", null, "ROLE_DIRECTOR"));

        List<ProgramEnrollmentResponse> responses = QueryCountAssertions.assertAtMostQueries(entityManagerFactory,
                PROGRAM_ENROLLMENTS_BY_STUDENT_QUERIES, () -> programEnrollmentController.listByStudent(student.getId()));

        assertThat(responses).hasSize(MANY);
        assertThat(responses).allSatisfy(response -> assertThat(response.programName()).startsWith("Program"));
    }

    @Test
    void sectionsBySubjectQueryCountDoesNotGrowWithSections() {
        Subject subject = persistSubject("Subject");
        for (int i = 0; i < MANY; i++) {
            persistSection(subject, persistTerm(i), "Section " + i);
        }
        entityManager.flush();
        entityManager.clear();

        List<SubjectSectionResponse> responses = QueryCountAssertions.assertAtMostQueries(entityManagerFactory,
                SECTIONS_BY_SUBJECT_QUERIES, () -> subjectSectionController.listBySubject(subject.getId(), null));

        assertThat(responses).hasSize(MANY);
        assertThat(responses).allSatisfy(response -> assertThat(response.subjectName()).isEqualTo("Subject"));
    }

    @Test
    void sectionInstructorsQueryCountDoesNotGrowWithInstructors() {
        SubjectSection section = persistSection(persistSubject("Subject"), persistTerm(0), "Section");
        for (int i = 0; i < MANY; i++) {
            Instructor instructor = new Instructor();
            instructor.setPerson(persistPerson());
            entityManager.persist(instructor);
            SubjectSectionInstructor assignment = new SubjectSectionInstructor();
            assignment.setSection(section);
            assignment.setInstructor(instructor);
            assignment.setRole(InstructorRole.LEAD);
            entityManager.persist(assignment);
        }
        entityManager.flush();
        entityManager.clear();

        List<SectionInstructorResponse> responses = QueryCountAssertions.assertAtMostQueries(entityManagerFactory,
                SECTION_INSTRUCTORS_QUERIES, () -> subjectSectionController.listInstructors(section.getId()));

        assertThat(responses).hasSize(MANY);
    }

    @Test
    void notificationsQueryCountDoesNotGrowWithNotifications() {
        Person recipient = persistPerson();
        for (int i = 0; i < MANY; i++) {
            Notification notification = new Notification();
            notification.setRecipient(recipient);
            notification.setType(NotificationType.values()[0]);
            notification.setMessage("Message " + i);
            notification.setCreatedAt(LocalDateTime.now());
            entityManager.persist(notification);
        }
        entityManager.flush();
        entityManager.clear();

        PageResponse<NotificationResponse> page = QueryCountAssertions.assertAtMostQueries(entityManagerFactory,
                NOTIFICATIONS_QUERIES, () -> listMyNotificationsUseCase.list(recipient.getId(), 0, 100));

        assertThat(page.items()).hasSize(MANY);
    }

    @Test
    void programEnrollmentsByStudentQueryCountPastTheBatchSizeGrowsOnlyByBatches() {
        Student student = persistStudent(persistInstitution());
        for (int i = 0; i < PAST_BATCH; i++) {
            persistProgramEnrollment(student, persistProgram("Program " + i));
        }
        entityManager.flush();
        entityManager.clear();
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken("director", null, "ROLE_DIRECTOR"));

        List<ProgramEnrollmentResponse> responses = QueryCountAssertions.assertAtMostQueries(entityManagerFactory,
                PROGRAM_ENROLLMENTS_PAST_BATCH_QUERIES,
                () -> programEnrollmentController.listByStudent(student.getId()));

        assertThat(responses).hasSize(PAST_BATCH);
    }

    @Test
    void sectionsBySubjectQueryCountPastTheBatchSizeGrowsOnlyByBatches() {
        Subject subject = persistSubject("Subject");
        for (int i = 0; i < PAST_BATCH; i++) {
            persistSection(subject, persistTerm(i), "Section " + i);
        }
        entityManager.flush();
        entityManager.clear();

        List<SubjectSectionResponse> responses = QueryCountAssertions.assertAtMostQueries(entityManagerFactory,
                SECTIONS_PAST_BATCH_QUERIES,
                () -> subjectSectionController.listBySubject(subject.getId(), null));

        assertThat(responses).hasSize(PAST_BATCH);
    }

    @Test
    void sectionInstructorsQueryCountPastTheBatchSizeGrowsOnlyByBatches() {
        SubjectSection section = persistSection(persistSubject("Subject"), persistTerm(0), "Section");
        for (int i = 0; i < PAST_BATCH; i++) {
            Instructor instructor = new Instructor();
            instructor.setPerson(persistPerson());
            entityManager.persist(instructor);
            SubjectSectionInstructor assignment = new SubjectSectionInstructor();
            assignment.setSection(section);
            assignment.setInstructor(instructor);
            assignment.setRole(InstructorRole.LEAD);
            entityManager.persist(assignment);
        }
        entityManager.flush();
        entityManager.clear();

        List<SectionInstructorResponse> responses = QueryCountAssertions.assertAtMostQueries(entityManagerFactory,
                INSTRUCTORS_PAST_BATCH_QUERIES,
                () -> subjectSectionController.listInstructors(section.getId()));

        assertThat(responses).hasSize(PAST_BATCH);
    }

    @Test
    void notificationsQueryCountPastTheBatchSizeGrowsOnlyByBatches() {
        Person recipient = persistPerson();
        for (int i = 0; i < PAST_BATCH; i++) {
            Notification notification = new Notification();
            notification.setRecipient(recipient);
            notification.setType(NotificationType.values()[0]);
            notification.setMessage("Message " + i);
            notification.setCreatedAt(LocalDateTime.now());
            entityManager.persist(notification);
        }
        entityManager.flush();
        entityManager.clear();

        PageResponse<NotificationResponse> page = QueryCountAssertions.assertAtMostQueries(entityManagerFactory,
                NOTIFICATIONS_PAST_BATCH_QUERIES,
                () -> listMyNotificationsUseCase.list(recipient.getId(), 0, 200));

        assertThat(page.items()).hasSize(MAX_NOTIFICATIONS_PAGE_SIZE);
    }

    private Program persistProgram(String name) {
        Program program = new Program();
        program.setName(name);
        program.setTotalYears(3);
        program.setTotalCreditsRequired(100);
        entityManager.persist(program);
        return program;
    }

    private void persistProgramEnrollment(Student student, Program program) {
        ProgramEnrollment programEnrollment = new ProgramEnrollment();
        programEnrollment.setStudent(student);
        programEnrollment.setProgram(program);
        programEnrollment.setStatus(ProgramEnrollmentStatus.ENROLLED);
        programEnrollment.setEnrollmentDate(LocalDateTime.now());
        entityManager.persist(programEnrollment);
    }

    private Subject persistSubject(String name) {
        Subject subject = new Subject();
        subject.setCode(TestSubjectCodes.next());
        subject.setName(name);
        entityManager.persist(subject);
        return subject;
    }

    private Term persistTerm(int index) {
        Term term = TermFixtures.newTerm();
        term.setYear(2000 + index);
        term.setDescription("Term " + index);
        term.setStatus(StatusType.ACTIVE);
        entityManager.persist(term);
        return term;
    }

    private SubjectSection persistSection(Subject subject, Term term, String name) {
        SubjectSection section = new SubjectSection();
        section.setName(name);
        section.setSubject(subject);
        section.setTerm(term);
        section.setMaxAbsenceRate(Percentage.ofPoints(BigDecimal.valueOf(25)));
        section.setStatus(StatusType.ACTIVE);
        entityManager.persist(section);
        return section;
    }

    private Institution persistInstitution() {
        Institution institution = new Institution();
        institution.setName("Perf institute");
        entityManager.persist(institution);
        return institution;
    }

    private Student persistStudent(Institution institution) {
        Student student = new Student();
        student.setPerson(persistPerson());
        student.setInstitution(institution);
        student.setOnboardingStatus(StudentOnboardingStatus.CONFIRMADO);
        entityManager.persist(student);
        return student;
    }

    private Person persistPerson() {
        String dni = String.valueOf(sequence++);
        Address address = new Address();
        entityManager.persist(address);
        Person person = new Person();
        person.setName("Name" + dni);
        person.setLastname("Last" + dni);
        person.setDni(dni);
        person.setUsername(dni);
        person.setEmail(dni + "@example.com");
        person.setAddress(address);
        person.setStatus(StatusType.ACTIVE);
        entityManager.persist(person);
        return person;
    }
}
