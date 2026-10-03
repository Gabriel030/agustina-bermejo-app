package com.red_escuela.backend.announcement;

import com.red_escuela.backend.config.TestSubjectCodes;

import com.red_escuela.backend.academicstructure.entity.Institution;
import com.red_escuela.backend.academicstructure.entity.Instructor;
import com.red_escuela.backend.academicstructure.entity.InstructorRole;
import com.red_escuela.backend.academicstructure.entity.Program;
import com.red_escuela.backend.academicstructure.entity.Subject;
import com.red_escuela.backend.academicstructure.entity.SubjectSection;
import com.red_escuela.backend.academicstructure.entity.SubjectSectionInstructor;
import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.announcement.entity.Announcement;
import com.red_escuela.backend.announcement.entity.AnnouncementCategory;
import com.red_escuela.backend.announcement.entity.AnnouncementStatus;
import com.red_escuela.backend.announcement.entity.AudienceSegmentKey;
import com.red_escuela.backend.announcement.entity.AudienceSpec;
import com.red_escuela.backend.announcement.entity.SectionType;
import com.red_escuela.backend.announcement.repository.AnnouncementRecipientRepository;
import com.red_escuela.backend.announcement.repository.AnnouncementRepository;
import com.red_escuela.backend.announcement.service.ScheduledAnnouncementDispatchJob;
import com.red_escuela.backend.announcement.usecase.announcement.CreateAnnouncementCommand;
import com.red_escuela.backend.announcement.usecase.announcement.CreateAnnouncementUseCase;
import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.entity.Address;
import com.red_escuela.backend.entity.StatusType;
import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentStatus;
import com.red_escuela.backend.enrollment.entity.SubjectSectionEnrollment;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.identity.entity.StudentOnboardingStatus;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class ScheduledAnnouncementDispatchJobTest extends AbstractIntegrationTest {

    @Autowired
    private ScheduledAnnouncementDispatchJob job;

    @Autowired
    private CreateAnnouncementUseCase createAnnouncement;

    @Autowired
    private AnnouncementRepository announcementRepository;

    @Autowired
    private AnnouncementRecipientRepository recipientRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private EntityManager entityManager;

    private static LocalDateTime clockAt(long minutesFromNow) {
        return LocalDateTime.now().plusMinutes(minutesFromNow).truncatedTo(ChronoUnit.MICROS);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void theJobSendsADueAnnouncementAndLeavesAFutureOneAlone() {
        Fixture fixture = aSectionWithStudents(2);
        authenticateAs(fixture.instructorPersonId);

        Long due = scheduled(fixture.sectionId, LocalDateTime.now().plusMinutes(10), null);
        Long future = scheduled(fixture.sectionId, LocalDateTime.now().plusDays(2), null);

        job.dispatchDue(clockAt(11));

        assertThat(statusOf(due)).isEqualTo(AnnouncementStatus.SENT);
        assertThat(sentAtOf(due)).isNotNull();
        assertThat(recipientRepository.findByAnnouncementId(due)).hasSize(2);

        assertThat(statusOf(future)).isEqualTo(AnnouncementStatus.SCHEDULED);
        assertThat(recipientRepository.findByAnnouncementId(future)).isEmpty();
    }

    @Test
    void twoConsecutiveRunsDoNotSendTheSameAnnouncementTwice() {
        Fixture fixture = aSectionWithStudents(2);
        authenticateAs(fixture.instructorPersonId);
        Long due = scheduled(fixture.sectionId, LocalDateTime.now().plusMinutes(5), null);

        LocalDateTime firstRun = clockAt(6);
        job.dispatchDue(firstRun);
        LocalDateTime afterFirstRun = sentAtOf(due);
        job.dispatchDue(firstRun.plusMinutes(5));

        assertThat(recipientRepository.findByAnnouncementId(due)).hasSize(2);
        assertThat(afterFirstRun).isEqualTo(firstRun);
        assertThat(sentAtOf(due)).isEqualTo(afterFirstRun);
    }

    @Test
    void anAnnouncementThatFailsDoesNotStopTheRestOfTheBatch() {
        Fixture fixture = aSectionWithStudents(2);
        authenticateAs(fixture.instructorPersonId);

        Long broken = scheduled(fixture.sectionId, LocalDateTime.now().plusMinutes(5),
                new AudienceSpec(AudienceSegmentKey.ABSENCES, null, null, 3));
        Long healthy = scheduled(fixture.sectionId, LocalDateTime.now().plusMinutes(5), null);

        job.dispatchDue(clockAt(6));

        assertThat(statusOf(broken)).isEqualTo(AnnouncementStatus.SCHEDULED);
        assertThat(statusOf(healthy)).isEqualTo(AnnouncementStatus.SENT);
        assertThat(recipientRepository.findByAnnouncementId(healthy)).hasSize(2);
    }

    @Test
    void theSegmentIsResolvedWhenItIsSentAndNotWhenItWasScheduled() {
        Fixture fixture = aSectionWithStudents(2);
        authenticateAs(fixture.instructorPersonId);

        Long due = scheduled(fixture.sectionId, LocalDateTime.now().plusMinutes(5),
                new AudienceSpec(AudienceSegmentKey.MUST_TAKE_FINAL, null, null, null));
        assertThat(recipientRepository.findByAnnouncementId(due)).isEmpty();

        markAsRegular(fixture.studentIds.getFirst());
        job.dispatchDue(clockAt(6));

        List<Long> reached = recipientRepository.findByAnnouncementId(due).stream()
                .map(recipient -> recipient.getStudent().getId())
                .toList();
        assertThat(reached).containsExactly(fixture.studentIds.getFirst());
    }

    private Long scheduled(Long sectionId, LocalDateTime scheduledFor, AudienceSpec spec) {
        return createAnnouncement.create(new CreateAnnouncementCommand(SectionType.PROGRAM, sectionId,
                "Cambio de aula del jueves",
                "La clase del jueves se dicta en el Aula 204, no en la 112.",
                AnnouncementCategory.SCHEDULING, false, null, scheduledFor, spec)).getId();
    }

    private AnnouncementStatus statusOf(Long announcementId) {
        return announcementRepository.findById(announcementId).orElseThrow().getStatus();
    }

    private LocalDateTime sentAtOf(Long announcementId) {
        return announcementRepository.findById(announcementId).orElseThrow().getSentAt();
    }

    private void markAsRegular(Long studentId) {
        transactionTemplate.executeWithoutResult(status -> entityManager.createQuery(
                        "update SubjectSectionEnrollment e set e.status = :status where e.student.id = :studentId")
                .setParameter("status", EnrollmentStatus.REGULAR)
                .setParameter("studentId", studentId)
                .executeUpdate());
    }

    private void authenticateAs(Long personId) {
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken(personId.toString(), null));
    }

    private record Fixture(Long instructorPersonId, Long sectionId, List<Long> studentIds) {
    }

    private Fixture aSectionWithStudents(int howMany) {
        return transactionTemplate.execute(status -> {
            Institution institution = persistInstitution();
            Subject subject = persistSubject("Análisis Matemático " + unique());
            Term term = persistTerm();
            SubjectSection section = persistSection(subject, term);
            Instructor instructor = persistInstructor("teacher" + unique());
            assignInstructor(section, instructor);
            Program program = persistProgram("Carrera " + unique());

            List<Long> studentIds = new ArrayList<>();
            for (int i = 0; i < howMany; i++) {
                Student student = persistStudent("student" + unique(), institution);
                ProgramEnrollment programEnrollment = persistProgramEnrollment(student, program);
                persistSectionEnrollment(student, programEnrollment, section);
                studentIds.add(student.getId());
            }
            entityManager.flush();
            return new Fixture(instructor.getPerson().getId(), section.getId(), studentIds);
        });
    }

    private String unique() {
        return String.valueOf(System.nanoTime() % 100000000L);
    }

    private Institution persistInstitution() {
        Institution institution = new Institution();
        institution.setName("Instituto " + unique());
        entityManager.persist(institution);
        return institution;
    }

    private Person persistPerson(String key) {
        Address address = new Address();
        entityManager.persist(address);
        Person person = new Person();
        person.setName("Persona");
        person.setLastname(key);
        person.setDni(key);
        person.setUsername(key);
        person.setEmail(key + "@example.com");
        person.setAddress(address);
        person.setStatus(StatusType.ACTIVE);
        entityManager.persist(person);
        return person;
    }

    private Student persistStudent(String key, Institution institution) {
        Student student = new Student();
        student.setPerson(persistPerson(key));
        student.setInstitution(institution);
        student.setOnboardingStatus(StudentOnboardingStatus.CONFIRMADO);
        entityManager.persist(student);
        return student;
    }

    private Instructor persistInstructor(String key) {
        Instructor instructor = new Instructor();
        instructor.setPerson(persistPerson(key));
        entityManager.persist(instructor);
        return instructor;
    }

    private Subject persistSubject(String name) {
        Subject subject = new Subject();
        subject.setCode(TestSubjectCodes.next());
        subject.setName(name);
        entityManager.persist(subject);
        return subject;
    }

    private Term persistTerm() {
        Term term = new Term();
        term.setYear(2026);
        term.setDescription("2026-1C");
        term.setStatus(StatusType.ACTIVE);
        entityManager.persist(term);
        return term;
    }

    private SubjectSection persistSection(Subject subject, Term term) {
        SubjectSection section = new SubjectSection();
        section.setName(subject.getName() + " - Com A");
        section.setSubject(subject);
        section.setTerm(term);
        section.setStatus(StatusType.ACTIVE);
        entityManager.persist(section);
        return section;
    }

    private void assignInstructor(SubjectSection section, Instructor instructor) {
        SubjectSectionInstructor assignment = new SubjectSectionInstructor();
        assignment.setSection(section);
        assignment.setInstructor(instructor);
        assignment.setRole(InstructorRole.LEAD);
        entityManager.persist(assignment);
    }

    private Program persistProgram(String name) {
        Program program = new Program();
        program.setName(name);
        program.setTotalYears(3);
        program.setTotalCreditsRequired(100);
        entityManager.persist(program);
        return program;
    }

    private ProgramEnrollment persistProgramEnrollment(Student student, Program program) {
        ProgramEnrollment enrollment = new ProgramEnrollment();
        enrollment.setStudent(student);
        enrollment.setProgram(program);
        enrollment.setStatus(ProgramEnrollmentStatus.ENROLLED);
        enrollment.setEnrollmentDate(LocalDateTime.now());
        entityManager.persist(enrollment);
        return enrollment;
    }

    private void persistSectionEnrollment(Student student, ProgramEnrollment programEnrollment, SubjectSection section) {
        SubjectSectionEnrollment enrollment = new SubjectSectionEnrollment();
        enrollment.setStudent(student);
        enrollment.setProgramEnrollment(programEnrollment);
        enrollment.setSubjectSection(section);
        enrollment.setStatus(EnrollmentStatus.IN_PROGRESS);
        enrollment.setEnrolledAt(LocalDateTime.now());
        enrollment.setAttemptNumber(1);
        entityManager.persist(enrollment);
    }
}
