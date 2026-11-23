package com.red_escuela.backend.attendance.specialist;

import com.red_escuela.backend.academicstructure.domain.InstructorRole;
import com.red_escuela.backend.academicstructure.entity.Instructor;
import com.red_escuela.backend.academicstructure.entity.Program;
import com.red_escuela.backend.academicstructure.entity.Subject;
import com.red_escuela.backend.academicstructure.entity.SubjectSection;
import com.red_escuela.backend.academicstructure.entity.SubjectSectionInstructor;
import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.attendance.dto.AttendanceEntry;
import com.red_escuela.backend.attendance.dto.CargarAsistenciaRequest;
import com.red_escuela.backend.attendance.entity.AttendanceStatus;
import com.red_escuela.backend.attendance.events.AttendanceStatusChangedEvent;
import com.red_escuela.backend.attendance.exception.ClassSessionAlreadyCanceledException;
import com.red_escuela.backend.attendance.usecase.record.CancelProgramSectionClassSessionCommand;
import com.red_escuela.backend.attendance.usecase.record.CancelProgramSectionClassSessionUseCase;
import com.red_escuela.backend.attendance.usecase.record.LoadProgramSectionAttendanceCommand;
import com.red_escuela.backend.attendance.usecase.record.LoadProgramSectionAttendanceUseCase;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class AttendanceLoadTransactionBoundaryIT extends AbstractIntegrationTest {

    @TestConfiguration
    static class FailingListenerConfiguration {
        @Bean
        FailingAttendanceListener failingAttendanceListener() {
            return new FailingAttendanceListener();
        }
    }

    static class FailingAttendanceListener {
        private final AtomicInteger seen = new AtomicInteger();
        private volatile int failOnNth;

        void failOnNthEvent(int nth) {
            seen.set(0);
            this.failOnNth = nth;
        }

        void stopFailing() {
            this.failOnNth = 0;
        }

        @EventListener
        void onStatusChanged(AttendanceStatusChangedEvent event) {
            int position = seen.incrementAndGet();
            if (failOnNth > 0 && position == failOnNth) {
                throw new IllegalStateException("listener failed on event " + position);
            }
        }
    }

    private static final LocalDate CLASS_DAY = LocalDate.of(2026, 3, 11);

    @Autowired
    private LoadProgramSectionAttendanceUseCase loadAttendance;

    @Autowired
    private CancelProgramSectionClassSessionUseCase cancelClassSession;

    @Autowired
    private FailingAttendanceListener failingListener;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private Long sectionId;
    private Long instructorId;
    private final List<Long> enrollmentIds = new ArrayList<>();

    @BeforeEach
    void seedASectionWithTwoEnrolledStudents() {
        long suffix = System.nanoTime() % 100000000L;
        enrollmentIds.clear();
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
            instructorId = instructor.getId();
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

            for (int i = 0; i < 2; i++) {
                Student student = new Student();
                student.setPerson(persistPerson("student" + i + "x" + suffix));
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
                enrollmentIds.add(enrollment.getId());
            }
        });
    }

    @AfterEach
    void cleanUp() {
        failingListener.stopFailing();
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

    private void loadBothStudents() {
        List<AttendanceEntry> entries = enrollmentIds.stream()
                .map(id -> new AttendanceEntry(id, AttendanceStatus.PRESENT))
                .toList();
        loadAttendance.load(new LoadProgramSectionAttendanceCommand(sectionId, instructorId,
                new CargarAsistenciaRequest(CLASS_DAY, entries, "Tema", "Obs")));
    }

    @Test
    void loadFailingWhilePublishingTheSecondEventLeavesNoAttendanceNorClassSession() {
        failingListener.failOnNthEvent(2);
        long attendanceBefore = count("attendance");
        long sessionsBefore = count("class_session");

        assertThatThrownBy(this::loadBothStudents)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("listener failed");

        assertThat(count("attendance")).isEqualTo(attendanceBefore);
        assertThat(count("class_session")).isEqualTo(sessionsBefore);
    }

    @Test
    void loadSucceedingPersistsEveryAttendanceAndTheClassSession() {
        failingListener.stopFailing();
        long attendanceBefore = count("attendance");
        long sessionsBefore = count("class_session");

        loadBothStudents();

        assertThat(count("attendance")).isEqualTo(attendanceBefore + 2);
        assertThat(count("class_session")).isEqualTo(sessionsBefore + 1);
    }

    @Test
    void cancelPersistsTheCanceledSessionAndRejectsASecondCancel() {
        LocalDate classDay = CLASS_DAY.plusDays(7);
        long sessionsBefore = count("class_session");

        cancelClassSession.cancel(new CancelProgramSectionClassSessionCommand(sectionId, instructorId, classDay));

        assertThat(count("class_session")).isEqualTo(sessionsBefore + 1);
        assertThat(jdbcTemplate.queryForObject(
                "select status from class_session where program_section_id = ? and class_date = ?",
                String.class, sectionId, classDay)).isEqualTo("CANCELED");
        assertThatThrownBy(() -> cancelClassSession.cancel(
                new CancelProgramSectionClassSessionCommand(sectionId, instructorId, classDay)))
                .isInstanceOf(ClassSessionAlreadyCanceledException.class);
        assertThat(count("class_session")).isEqualTo(sessionsBefore + 1);
    }
}
