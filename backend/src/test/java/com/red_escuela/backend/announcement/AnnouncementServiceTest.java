package com.red_escuela.backend.announcement;

import com.red_escuela.backend.config.TestSubjectCodes;

import com.red_escuela.backend.identity.entity.Institution;
import com.red_escuela.backend.academicstructure.entity.Instructor;
import com.red_escuela.backend.academicstructure.entity.InstructorRole;
import com.red_escuela.backend.academicstructure.entity.Program;
import com.red_escuela.backend.academicstructure.entity.Subject;
import com.red_escuela.backend.academicstructure.entity.SubjectSection;
import com.red_escuela.backend.academicstructure.entity.SubjectSectionInstructor;
import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.announcement.entity.Announcement;
import com.red_escuela.backend.announcement.entity.AnnouncementRecipient;
import com.red_escuela.backend.announcement.entity.AnnouncementStatus;
import com.red_escuela.backend.announcement.entity.AudienceSegmentKey;
import com.red_escuela.backend.announcement.entity.AudienceSpec;
import com.red_escuela.backend.announcement.entity.AnnouncementCategory;
import com.red_escuela.backend.announcement.entity.SectionType;
import com.red_escuela.backend.announcement.exception.AnnouncementAlreadySentException;
import com.red_escuela.backend.announcement.exception.CannotChangeAudienceOfSentAnnouncementException;
import com.red_escuela.backend.announcement.exception.EmptyAudienceException;
import com.red_escuela.backend.announcement.exception.ScheduledDateInThePastException;
import com.red_escuela.backend.announcement.exception.NotSectionInstructorException;
import com.red_escuela.backend.announcement.repository.AnnouncementRecipientRepository;
import com.red_escuela.backend.announcement.usecase.announcement.CreateAnnouncementCommand;
import com.red_escuela.backend.announcement.usecase.announcement.CreateAnnouncementUseCase;
import com.red_escuela.backend.announcement.usecase.announcement.DeleteAnnouncementUseCase;
import com.red_escuela.backend.announcement.usecase.announcement.ListMyAnnouncementsUseCase;
import com.red_escuela.backend.announcement.usecase.announcement.UpdateAnnouncementCommand;
import com.red_escuela.backend.announcement.usecase.announcement.UpdateAnnouncementUseCase;
import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.shared.domain.Address;
import com.red_escuela.backend.shared.domain.StatusType;
import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentStatus;
import com.red_escuela.backend.enrollment.entity.SubjectSectionEnrollment;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.identity.entity.StudentOnboardingStatus;
import com.red_escuela.backend.notification.entity.Notification;
import com.red_escuela.backend.notification.entity.NotificationType;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AnnouncementServiceTest extends AbstractIntegrationTest {

    @Autowired
    private CreateAnnouncementUseCase createAnnouncement;

    @Autowired
    private DeleteAnnouncementUseCase deleteAnnouncement;

    @Autowired
    private ListMyAnnouncementsUseCase listMyAnnouncements;

    @Autowired
    private UpdateAnnouncementUseCase updateAnnouncement;

    @Autowired
    private AnnouncementRecipientRepository recipientRepository;

    @Autowired
    private EntityManager entityManager;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createFailsWhenTheInstructorDoesNotTeachTheSection() {
        Fixture fixture = aSectionWithStudents(2);
        Instructor outsider = persistInstructor("outsider" + unique());
        authenticateAs(outsider.getPerson());

        CreateAnnouncementCommand command = aCommand(fixture.section.getId(), null);

        assertThatThrownBy(() -> createAnnouncement.create(command))
                .isInstanceOf(NotSectionInstructorException.class);
    }

    @Test
    void createNotifiesOneRecipientPerEnrolledStudentAndNoMore() {
        Fixture fixture = aSectionWithStudents(3);
        authenticateAs(fixture.instructor.getPerson());

        Announcement announcement = createAnnouncement.create(aCommand(fixture.section.getId(), null));
        entityManager.flush();

        List<Notification> notifications = notificationsFor(announcement.getId());
        assertThat(notifications).hasSize(3);
        assertThat(notifications).allMatch(n -> n.getType() == NotificationType.ANNOUNCEMENT_PUBLISHED);
        assertThat(notifications).extracting(Notification::getRecipient).extracting(Person::getId)
                .containsExactlyInAnyOrderElementsOf(fixture.studentPersonIds());
    }

    @Test
    void theNotificationIsAPointerAndNeverCarriesTheBody() {
        Fixture fixture = aSectionWithStudents(1);
        authenticateAs(fixture.instructor.getPerson());

        Announcement announcement = createAnnouncement.create(aCommand(fixture.section.getId(), null));
        entityManager.flush();

        Notification notification = notificationsFor(announcement.getId()).getFirst();
        assertThat(notification.getMessage()).doesNotContain(announcement.getBody());
        assertThat(notification.getMessage()).contains(fixture.subject.getName());
        assertThat(notification.getRelatedEntityType()).isEqualTo("Announcement");
        assertThat(notification.getRelatedEntityId()).isEqualTo(announcement.getId());
    }

    @Test
    void deleteRemovesTheUnreadPointersSoNoneIsLeftDangling() {
        Fixture fixture = aSectionWithStudents(2);
        authenticateAs(fixture.instructor.getPerson());
        Announcement announcement = createAnnouncement.create(aCommand(fixture.section.getId(), null));
        entityManager.flush();
        assertThat(notificationsFor(announcement.getId())).hasSize(2);

        deleteAnnouncement.delete(announcement.getId());
        entityManager.flush();

        assertThat(notificationsFor(announcement.getId())).isEmpty();
    }

    @Test
    void theEntityRejectsHavingBothSectionsOrNone() {
        Announcement orphan = Announcement.builder()
                .title("t").body("b").category(AnnouncementCategory.REMINDER)
                .audienceLabel("Toda la comisión").createdAt(LocalDateTime.now())
                .createdBy(persistPerson("orphan" + unique()))
                .build();

        assertThatThrownBy(() -> {
            entityManager.persist(orphan);
            entityManager.flush();
        }).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void theStudentFeedExcludesAnnouncementsThatAlreadyExpired() {
        Fixture fixture = aSectionWithStudents(1);
        authenticateAs(fixture.instructor.getPerson());
        createAnnouncement.create(aCommand(fixture.section.getId(), LocalDate.now().minusDays(1)));
        createAnnouncement.create(aCommand(fixture.section.getId(), LocalDate.now().plusDays(1)));
        entityManager.flush();

        authenticateAs(fixture.students.getFirst().getPerson());
        List<Announcement> feed = listMyAnnouncements.listForCurrentStudent();

        assertThat(feed).hasSize(1);
        assertThat(feed.getFirst().getExpiresAt()).isAfterOrEqualTo(LocalDate.now());
    }

    @Test
    void theStudentFeedIsEmptyAndNotAnErrorForSomeoneWithoutEnrollments() {
        Institution institution = persistInstitution();
        Student loner = persistStudent("loner" + unique(), institution);
        entityManager.flush();
        authenticateAs(loner.getPerson());

        assertThat(listMyAnnouncements.listForCurrentStudent()).isEmpty();
    }

    @Test
    void publishingToASegmentFreezesTheRecipientListAtThatMoment() {
        Fixture fixture = aSectionWithStudents(3);
        authenticateAs(fixture.instructor.getPerson());
        markAsRegular(fixture.students.getFirst());
        entityManager.flush();

        Announcement announcement = createAnnouncement.create(
                aCommand(fixture.section.getId(), null,
                        new AudienceSpec(AudienceSegmentKey.MUST_TAKE_FINAL, null, null, null)));
        entityManager.flush();

        List<AnnouncementRecipient> recipients = recipientRepository.findByAnnouncementId(announcement.getId());
        assertThat(recipients).hasSize(1);
        assertThat(recipients.getFirst().getStudent().getId()).isEqualTo(fixture.students.getFirst().getId());
        assertThat(announcement.getAudienceLabel()).isEqualTo("Tienen que rendir el final");
    }

    @Test
    void aSegmentThatReachesNobodyCannotBePublished() {
        Fixture fixture = aSectionWithStudents(2);
        authenticateAs(fixture.instructor.getPerson());

        CreateAnnouncementCommand command = aCommand(fixture.section.getId(), null,
                new AudienceSpec(AudienceSegmentKey.MUST_TAKE_FINAL, null, null, null));

        assertThatThrownBy(() -> createAnnouncement.create(command))
                .isInstanceOf(EmptyAudienceException.class);
    }

    @Test
    void onlyTheFrozenRecipientsSeeASegmentedAnnouncement() {
        Fixture fixture = aSectionWithStudents(2);
        authenticateAs(fixture.instructor.getPerson());
        markAsRegular(fixture.students.getFirst());
        entityManager.flush();

        createAnnouncement.create(aCommand(fixture.section.getId(), null,
                new AudienceSpec(AudienceSegmentKey.MUST_TAKE_FINAL, null, null, null)));
        entityManager.flush();

        authenticateAs(fixture.students.getFirst().getPerson());
        assertThat(listMyAnnouncements.listForCurrentStudent()).hasSize(1);

        authenticateAs(fixture.students.get(1).getPerson());
        assertThat(listMyAnnouncements.listForCurrentStudent()).isEmpty();
    }

    @Test
    void markingASentAnnouncementAsSentAgainFails() {
        Announcement sent = Announcement.builder()
                .title("t").body("b").category(AnnouncementCategory.REMINDER)
                .audienceLabel("Toda la comisión").createdAt(LocalDateTime.now())
                .status(AnnouncementStatus.SENT).sentAt(LocalDateTime.now())
                .build();

        assertThatThrownBy(() -> sent.markAsSent(LocalDateTime.now()))
                .isInstanceOf(AnnouncementAlreadySentException.class);
    }

    @Test
    void aScheduledAnnouncementFreezesNobodyAndNotifiesNobody() {
        Fixture fixture = aSectionWithStudents(2);
        authenticateAs(fixture.instructor.getPerson());

        Announcement announcement = createAnnouncement.create(
                aCommand(fixture.section.getId(), null, null, LocalDateTime.now().plusDays(1)));
        entityManager.flush();

        assertThat(announcement.getStatus()).isEqualTo(AnnouncementStatus.SCHEDULED);
        assertThat(announcement.getSentAt()).isNull();
        assertThat(recipientRepository.findByAnnouncementId(announcement.getId())).isEmpty();
        assertThat(notificationsFor(announcement.getId())).isEmpty();
    }

    @Test
    void theStudentFeedExcludesScheduledAnnouncements() {
        Fixture fixture = aSectionWithStudents(1);
        authenticateAs(fixture.instructor.getPerson());
        createAnnouncement.create(aCommand(fixture.section.getId(), null, null, LocalDateTime.now().plusHours(2)));
        entityManager.flush();

        authenticateAs(fixture.students.getFirst().getPerson());
        assertThat(listMyAnnouncements.listForCurrentStudent()).isEmpty();
    }

    @Test
    void schedulingForAMomentAlreadyPastIsRejected() {
        Fixture fixture = aSectionWithStudents(1);
        authenticateAs(fixture.instructor.getPerson());

        CreateAnnouncementCommand command =
                aCommand(fixture.section.getId(), null, null, LocalDateTime.now().minusMinutes(1));

        assertThatThrownBy(() -> createAnnouncement.create(command))
                .isInstanceOf(ScheduledDateInThePastException.class);
    }

    @Test
    void aScheduledSegmentThatReachesNobodyTodayIsStillAccepted() {
        Fixture fixture = aSectionWithStudents(2);
        authenticateAs(fixture.instructor.getPerson());

        Announcement announcement = createAnnouncement.create(
                aCommand(fixture.section.getId(), null,
                        new AudienceSpec(AudienceSegmentKey.MUST_TAKE_FINAL, null, null, null),
                        LocalDateTime.now().plusDays(1)));

        assertThat(announcement.getStatus()).isEqualTo(AnnouncementStatus.SCHEDULED);
    }

    @Test
    void theAudienceOfASentAnnouncementCannotBeChanged() {
        Fixture fixture = aSectionWithStudents(2);
        authenticateAs(fixture.instructor.getPerson());
        Announcement sent = createAnnouncement.create(aCommand(fixture.section.getId(), null));
        entityManager.flush();

        UpdateAnnouncementCommand command = new UpdateAnnouncementCommand(sent.getId(), "Otro título",
                "Otro cuerpo", AnnouncementCategory.REMINDER, false, null, null,
                new AudienceSpec(AudienceSegmentKey.MUST_TAKE_FINAL, null, null, null));

        assertThatThrownBy(() -> updateAnnouncement.update(command))
                .isInstanceOf(CannotChangeAudienceOfSentAnnouncementException.class);
    }

    @Test
    void theAudienceOfAScheduledAnnouncementCanBeChangedAndNobodyIsNotified() {
        Fixture fixture = aSectionWithStudents(2);
        authenticateAs(fixture.instructor.getPerson());
        markAsRegular(fixture.students.getFirst());
        entityManager.flush();
        Announcement scheduled = createAnnouncement.create(
                aCommand(fixture.section.getId(), null, null, LocalDateTime.now().plusDays(1)));
        entityManager.flush();

        Announcement updated = updateAnnouncement.update(new UpdateAnnouncementCommand(scheduled.getId(),
                "Otro título", "Otro cuerpo", AnnouncementCategory.REMINDER, false, null, null,
                new AudienceSpec(AudienceSegmentKey.MUST_TAKE_FINAL, null, null, null)));
        entityManager.flush();

        assertThat(updated.getAudienceLabel()).isEqualTo("Tienen que rendir el final");
        assertThat(updated.getStatus()).isEqualTo(AnnouncementStatus.SCHEDULED);
        assertThat(notificationsFor(updated.getId())).isEmpty();
    }

    private void markAsRegular(Student student) {
        entityManager.createQuery(
                        "update SubjectSectionEnrollment e set e.status = :status where e.student.id = :studentId")
                .setParameter("status", EnrollmentStatus.REGULAR)
                .setParameter("studentId", student.getId())
                .executeUpdate();
        entityManager.clear();
    }

    private record Fixture(Instructor instructor, SubjectSection section, Subject subject, List<Student> students) {
        List<Long> studentPersonIds() {
            return students.stream().map(s -> s.getPerson().getId()).toList();
        }
    }

    private Fixture aSectionWithStudents(int howMany) {
        Institution institution = persistInstitution();
        Subject subject = persistSubject("Análisis Matemático " + unique());
        Term term = persistTerm();
        SubjectSection section = persistSection(subject, term);
        Instructor instructor = persistInstructor("teacher" + unique());
        assignInstructor(section, instructor);
        Program program = persistProgram("Carrera " + unique());

        List<Student> students = new java.util.ArrayList<>();
        for (int i = 0; i < howMany; i++) {
            Student student = persistStudent("student" + unique(), institution);
            ProgramEnrollment programEnrollment = persistProgramEnrollment(student, program);
            persistSectionEnrollment(student, programEnrollment, section);
            students.add(student);
        }
        entityManager.flush();
        return new Fixture(instructor, section, subject, students);
    }

    private CreateAnnouncementCommand aCommand(Long sectionId, LocalDate expiresAt) {
        return aCommand(sectionId, expiresAt, null);
    }

    private CreateAnnouncementCommand aCommand(Long sectionId, LocalDate expiresAt, AudienceSpec audienceSpec) {
        return aCommand(sectionId, expiresAt, audienceSpec, null);
    }

    private CreateAnnouncementCommand aCommand(Long sectionId, LocalDate expiresAt, AudienceSpec audienceSpec,
                                               LocalDateTime scheduledFor) {
        return new CreateAnnouncementCommand(SectionType.PROGRAM, sectionId,
                "Cambio de aula del jueves",
                "La clase del jueves se dicta en el Aula 204, no en la 112.",
                AnnouncementCategory.SCHEDULING, false, expiresAt, scheduledFor, audienceSpec);
    }

    private List<Notification> notificationsFor(Long announcementId) {
        return entityManager.createQuery(
                        "select n from Notification n where n.relatedEntityType = 'Announcement' and n.relatedEntityId = :id",
                        Notification.class)
                .setParameter("id", announcementId)
                .getResultList();
    }

    private void authenticateAs(Person person) {
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken(person.getId().toString(), null));
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
