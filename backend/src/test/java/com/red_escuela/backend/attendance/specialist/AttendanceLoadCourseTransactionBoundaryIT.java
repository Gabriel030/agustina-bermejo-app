package com.red_escuela.backend.attendance.specialist;

import com.red_escuela.backend.academicstructure.CycleTestFixtures;
import com.red_escuela.backend.academicstructure.domain.InstructorRole;
import com.red_escuela.backend.academicstructure.entity.Course;
import com.red_escuela.backend.academicstructure.entity.CourseSubject;
import com.red_escuela.backend.academicstructure.entity.CourseSubjectSection;
import com.red_escuela.backend.academicstructure.entity.CourseSubjectSectionInstructor;
import com.red_escuela.backend.academicstructure.entity.Instructor;
import com.red_escuela.backend.academicstructure.entity.TeachingMode;
import com.red_escuela.backend.attendance.dto.AttendanceEntry;
import com.red_escuela.backend.attendance.dto.CargarAsistenciaRequest;
import com.red_escuela.backend.attendance.entity.AttendanceStatus;
import com.red_escuela.backend.attendance.events.AttendanceStatusChangedEvent;
import com.red_escuela.backend.attendance.usecase.record.LoadCourseSectionAttendanceCommand;
import com.red_escuela.backend.attendance.usecase.record.LoadCourseSectionAttendanceUseCase;
import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.enrollment.entity.CourseEnrollment;
import com.red_escuela.backend.enrollment.entity.CourseSubjectSectionEnrollment;
import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.identity.entity.Institution;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.identity.entity.StudentOnboardingStatus;
import com.red_escuela.backend.shared.domain.StatusType;
import com.red_escuela.backend.testsupport.FailingEventListenersConfiguration;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Import(FailingEventListenersConfiguration.class)
@ActiveProfiles("test")
class AttendanceLoadCourseTransactionBoundaryIT extends AbstractIntegrationTest {

    private static final LocalDate CLASS_DAY = LocalDate.of(2026, 3, 11);

    @Autowired
    private LoadCourseSectionAttendanceUseCase loadAttendance;

    @Autowired
    private FailingEventListenersConfiguration.FailingAttendanceListener failingListener;

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
    void seedACourseSectionWithTwoEnrolledStudents() {
        enrollmentIds.clear();
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            CycleTestFixtures fixtures = new CycleTestFixtures(entityManager);
            Course course = new Course();
            course.setName("Course " + System.nanoTime());
            course.setTerm(fixtures.term(2400 + (int) (System.nanoTime() % 90)));
            course.setCapacity(10);
            course.setIsActive(StatusType.ACTIVE);
            course.setTeachingMode(TeachingMode.ONLINE);
            entityManager.persist(course);
            CourseSubject module = new CourseSubject();
            module.setCourse(course);
            module.setName("Module " + course.getName());
            module.setStatus(StatusType.ACTIVE);
            entityManager.persist(module);
            CourseSubjectSection section = new CourseSubjectSection();
            section.setName("Section " + course.getName());
            section.setCourseSubject(module);
            section.setTerm(course.getTerm());
            section.setCapacity(10);
            section.setStatus(StatusType.ACTIVE);
            entityManager.persist(section);
            sectionId = section.getId();

            Instructor instructor = fixtures.instructor();
            instructorId = instructor.getId();
            CourseSubjectSectionInstructor assignment = new CourseSubjectSectionInstructor();
            assignment.setSection(section);
            assignment.setInstructor(instructor);
            assignment.setRole(InstructorRole.LEAD);
            entityManager.persist(assignment);

            Institution institution = new Institution();
            institution.setName("Test institute");
            entityManager.persist(institution);
            for (int i = 0; i < 2; i++) {
                Student student = new Student();
                student.setPerson(fixtures.person());
                student.setInstitution(institution);
                student.setOnboardingStatus(StudentOnboardingStatus.CONFIRMADO);
                entityManager.persist(student);
                CourseEnrollment parent = new CourseEnrollment();
                parent.setStudent(student);
                parent.setCourse(course);
                parent.setStatus(EnrollmentStatus.ENROLLED);
                parent.setEnrollmentDate(LocalDateTime.now());
                entityManager.persist(parent);
                CourseSubjectSectionEnrollment enrollment = new CourseSubjectSectionEnrollment();
                enrollment.setStudent(student);
                enrollment.setCourseEnrollment(parent);
                enrollment.setCourseSubjectSection(section);
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

    private long count(String table) {
        return jdbcTemplate.queryForObject("select count(*) from " + table, Long.class);
    }

    private void loadBothStudents() {
        List<AttendanceEntry> entries = enrollmentIds.stream()
                .map(id -> new AttendanceEntry(id, AttendanceStatus.PRESENT))
                .toList();
        loadAttendance.load(new LoadCourseSectionAttendanceCommand(sectionId, instructorId,
                new CargarAsistenciaRequest(CLASS_DAY, entries, "Topic", "Notes")));
    }

    @Test
    void courseLoadFailingWhilePublishingAnEventLeavesNoAttendanceNorClassSession() {
        failingListener.failOn(AttendanceStatusChangedEvent.class);
        long attendanceBefore = count("attendance");
        long sessionsBefore = count("class_session");

        assertThatThrownBy(this::loadBothStudents)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("listener failed");

        assertThat(count("attendance")).isEqualTo(attendanceBefore);
        assertThat(count("class_session")).isEqualTo(sessionsBefore);
    }

    @Test
    void courseLoadSucceedingPersistsEveryAttendanceAndTheClassSession() {
        long attendanceBefore = count("attendance");
        long sessionsBefore = count("class_session");

        loadBothStudents();

        assertThat(count("attendance")).isEqualTo(attendanceBefore + 2);
        assertThat(count("class_session")).isEqualTo(sessionsBefore + 1);
    }
}
