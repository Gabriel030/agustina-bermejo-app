package com.red_escuela.backend.announcement.specialist;

import com.red_escuela.backend.academicstructure.domain.InstructorRole;
import com.red_escuela.backend.academicstructure.entity.Instructor;
import com.red_escuela.backend.academicstructure.entity.Program;
import com.red_escuela.backend.academicstructure.entity.Subject;
import com.red_escuela.backend.academicstructure.entity.SubjectSection;
import com.red_escuela.backend.academicstructure.entity.SubjectSectionInstructor;
import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.announcement.entity.Announcement;
import com.red_escuela.backend.announcement.entity.AnnouncementCategory;
import com.red_escuela.backend.announcement.entity.SectionType;
import com.red_escuela.backend.announcement.events.AnnouncementDeletedEvent;
import com.red_escuela.backend.announcement.events.AnnouncementPublishedEvent;
import com.red_escuela.backend.announcement.events.AnnouncementUpdatedEvent;
import com.red_escuela.backend.announcement.usecase.broadcast.CreateAnnouncementCommand;
import com.red_escuela.backend.announcement.usecase.broadcast.CreateAnnouncementUseCase;
import com.red_escuela.backend.announcement.usecase.broadcast.DeleteAnnouncementUseCase;
import com.red_escuela.backend.announcement.usecase.broadcast.UpdateAnnouncementCommand;
import com.red_escuela.backend.announcement.usecase.broadcast.UpdateAnnouncementUseCase;
import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.config.TestSubjectCodes;
import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentStatus;
import com.red_escuela.backend.enrollment.entity.SubjectSectionEnrollment;
import com.red_escuela.backend.identity.entity.Institution;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.identity.entity.StudentOnboardingStatus;
import com.red_escuela.backend.shared.domain.Address;
import com.red_escuela.backend.shared.domain.StatusType;
import com.red_escuela.backend.testsupport.TermFixtures;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class AnnouncementTransactionBoundaryIT extends AbstractIntegrationTest {

    @TestConfiguration
    static class FailingListenerConfiguration {
        @Bean
        FailingAnnouncementListener failingAnnouncementListener() {
            return new FailingAnnouncementListener();
        }
    }

    static class FailingAnnouncementListener {
        private volatile Class<?> failingOn;

        void failOn(Class<?> eventType) {
            this.failingOn = eventType;
        }

        void stopFailing() {
            this.failingOn = null;
        }

        @EventListener
        void onPublished(AnnouncementPublishedEvent event) {
            failIfArmedFor(AnnouncementPublishedEvent.class);
        }

        @EventListener
        void onUpdated(AnnouncementUpdatedEvent event) {
            failIfArmedFor(AnnouncementUpdatedEvent.class);
        }

        @EventListener
        void onDeleted(AnnouncementDeletedEvent event) {
            failIfArmedFor(AnnouncementDeletedEvent.class);
        }

        private void failIfArmedFor(Class<?> eventType) {
            if (eventType.equals(failingOn)) {
                throw new IllegalStateException("listener failed on " + eventType.getSimpleName());
            }
        }
    }

    private static final String ORIGINAL_TITLE = "Cambio de aula del jueves";

    @Autowired
    private CreateAnnouncementUseCase createAnnouncement;

    @Autowired
    private UpdateAnnouncementUseCase updateAnnouncement;

    @Autowired
    private DeleteAnnouncementUseCase deleteAnnouncement;

    @Autowired
    private FailingAnnouncementListener failingListener;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private Long sectionId;

    @BeforeEach
    void seedASectionWithOneEnrolledStudent() {
        long suffix = System.nanoTime() % 100000000L;
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            Institution institution = new Institution();
            institution.setName("Instituto " + suffix);
            entityManager.persist(institution);

            Subject subject = new Subject();
            subject.setCode(TestSubjectCodes.next());
            subject.setName("Analisis " + suffix);
            entityManager.persist(subject);

            Term term = TermFixtures.newTerm();
            term.setYear(2026);
            term.setDescription("2026-1C");
            term.setStatus(StatusType.ACTIVE);
            entityManager.persist(term);

            SubjectSection section = new SubjectSection();
            section.setName("Comision " + suffix);
            section.setSubject(subject);
            section.setTerm(term);
            section.setStatus(StatusType.ACTIVE);
            entityManager.persist(section);
            sectionId = section.getId();

            Instructor instructor = new Instructor();
            instructor.setPerson(persistPerson("teacher" + suffix));
            entityManager.persist(instructor);
            SubjectSectionInstructor assignment = new SubjectSectionInstructor();
            assignment.setSection(section);
            assignment.setInstructor(instructor);
            assignment.setRole(InstructorRole.LEAD);
            entityManager.persist(assignment);

            Program program = new Program();
            program.setName("Carrera " + suffix);
            program.setTotalYears(3);
            program.setTotalCreditsRequired(100);
            entityManager.persist(program);

            Student student = new Student();
            student.setPerson(persistPerson("student" + suffix));
            student.setInstitution(institution);
            student.setOnboardingStatus(StudentOnboardingStatus.CONFIRMADO);
            entityManager.persist(student);

            ProgramEnrollment programEnrollment = new ProgramEnrollment();
            programEnrollment.setStudent(student);
            programEnrollment.setProgram(program);
            programEnrollment.setStatus(ProgramEnrollmentStatus.ENROLLED);
            programEnrollment.setEnrollmentDate(LocalDateTime.now());
            entityManager.persist(programEnrollment);

            SubjectSectionEnrollment enrollment = new SubjectSectionEnrollment();
            enrollment.setStudent(student);
            enrollment.setProgramEnrollment(programEnrollment);
            enrollment.setSubjectSection(section);
            enrollment.setStatus(EnrollmentStatus.IN_PROGRESS);
            enrollment.setEnrolledAt(LocalDateTime.now());
            enrollment.setAttemptNumber(1);
            entityManager.persist(enrollment);

            SecurityContextHolder.getContext().setAuthentication(
                    new TestingAuthenticationToken(instructor.getPerson().getId().toString(), null));
        });
    }

    @AfterEach
    void cleanUp() {
        failingListener.stopFailing();
        SecurityContextHolder.clearContext();
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

    private long count(String table) {
        return jdbcTemplate.queryForObject("select count(*) from " + table, Long.class);
    }

    private Announcement publishAnnouncement() {
        return createAnnouncement.create(new CreateAnnouncementCommand(SectionType.PROGRAM, sectionId, ORIGINAL_TITLE,
                "La clase del jueves se dicta en el Aula 204.", AnnouncementCategory.SCHEDULING, false, null, null, null));
    }

    @Test
    void publishingFailingAfterTheRecipientsAreFrozenLeavesNeitherTheAnnouncementNorItsRecipients() {
        failingListener.failOn(AnnouncementPublishedEvent.class);
        long announcementsBefore = count("announcement");
        long recipientsBefore = count("announcement_recipient");

        assertThatThrownBy(this::publishAnnouncement)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("listener failed");

        assertThat(count("announcement")).isEqualTo(announcementsBefore);
        assertThat(count("announcement_recipient")).isEqualTo(recipientsBefore);
    }

    @Test
    void updatingFailingWhileRenotifyingKeepsTheOriginalTitle() {
        Long announcementId = publishAnnouncement().getId();
        failingListener.failOn(AnnouncementUpdatedEvent.class);

        assertThatThrownBy(() -> updateAnnouncement.update(new UpdateAnnouncementCommand(announcementId,
                "Titulo nuevo", null, null, false, null, null, null, true)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("listener failed");

        assertThat(jdbcTemplate.queryForObject("select title from announcement where id = ?", String.class, announcementId))
                .isEqualTo(ORIGINAL_TITLE);
    }

    @Test
    void deletingFailingWhilePublishingTheEventKeepsTheAnnouncementAndItsRecipients() {
        Long announcementId = publishAnnouncement().getId();
        failingListener.failOn(AnnouncementDeletedEvent.class);
        long recipientsBefore = count("announcement_recipient");

        assertThatThrownBy(() -> deleteAnnouncement.delete(announcementId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("listener failed");

        assertThat(jdbcTemplate.queryForObject("select count(*) from announcement where id = ?", Long.class, announcementId))
                .isEqualTo(1L);
        assertThat(count("announcement_recipient")).isEqualTo(recipientsBefore);
    }
}
