package com.red_escuela.backend.enrollment.service;

import com.red_escuela.backend.academicstructure.CycleTestFixtures;
import com.red_escuela.backend.academicstructure.entity.AcademicOfferingCycle;
import com.red_escuela.backend.academicstructure.entity.AcademicOfferingCycleStatus;
import com.red_escuela.backend.academicstructure.entity.Course;
import com.red_escuela.backend.academicstructure.entity.CourseSubject;
import com.red_escuela.backend.academicstructure.entity.CourseSubjectSection;
import com.red_escuela.backend.academicstructure.entity.Program;
import com.red_escuela.backend.academicstructure.entity.SubjectSection;
import com.red_escuela.backend.academicstructure.entity.TeachingMode;
import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.academicstructure.events.CourseCapacityChangedEvent;
import com.red_escuela.backend.academicstructure.domain.CourseNotFoundException;
import com.red_escuela.backend.academicstructure.events.CourseSectionCapacityChangedEvent;
import com.red_escuela.backend.academicstructure.events.SectionCapacityChangedEvent;
import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.enrollment.entity.CourseEnrollment;
import com.red_escuela.backend.enrollment.entity.CourseSubjectSectionEnrollment;
import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentStatus;
import com.red_escuela.backend.enrollment.entity.SubjectSectionEnrollment;
import com.red_escuela.backend.identity.entity.Institution;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.identity.entity.StudentOnboardingStatus;
import com.red_escuela.backend.shared.domain.StatusType;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class WaitlistCapacityChangeListenerAtomicityIT extends AbstractIntegrationTest {

    private static final AtomicLong YEARS = new AtomicLong(2200);
    private static final LocalDateTime LONG_AFTER_ANY_TEST = LocalDateTime.of(2400, 1, 1, 0, 0);

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private String sabotagedTable;

    @AfterEach
    void removePromotionSabotage() {
        if (sabotagedTable != null) {
            jdbcTemplate.execute("drop trigger if exists fail_waitlist_promotion on " + sabotagedTable);
            jdbcTemplate.execute("drop function if exists fail_waitlist_promotion()");
            sabotagedTable = null;
        }
    }

    @Test
    void programSectionCapacityIncreaseWhosePromotionFailsRevertsTheCapacityChange() {
        SeededProgramSection seeded = seedProgramSectionWithOneWaitingStudent();
        failPromotionsOn("subject_section_enrollment");

        assertThatThrownBy(() -> inOneTransaction(() -> {
            raiseProgramSectionCapacity(seeded.sectionId());
            eventPublisher.publishEvent(SectionCapacityChangedEvent.of(seeded.sectionId(), 1, 2));
        })).hasMessageContaining("waitlist promotion failed");

        assertThat(capacityOf("subject_section", seeded.sectionId())).isEqualTo(1);
        assertThat(statusOf("subject_section_enrollment", seeded.waitingEnrollmentId())).isEqualTo("WAITLISTED");
    }

    @Test
    void programSectionCapacityIncreaseWhosePromotionSucceedsCommitsTheCapacityAndThePromotion() {
        SeededProgramSection seeded = seedProgramSectionWithOneWaitingStudent();

        inOneTransaction(() -> {
            raiseProgramSectionCapacity(seeded.sectionId());
            eventPublisher.publishEvent(SectionCapacityChangedEvent.of(seeded.sectionId(), 1, 2));
        });

        assertThat(capacityOf("subject_section", seeded.sectionId())).isEqualTo(2);
        assertThat(statusOf("subject_section_enrollment", seeded.waitingEnrollmentId())).isEqualTo("ENROLLED");
    }

    @Test
    void courseSectionCapacityIncreaseWhosePromotionFailsRevertsTheCapacityChange() {
        SeededCourseSection seeded = seedCourseSectionWithOneWaitingStudent();
        failPromotionsOn("course_section_enrollment");

        assertThatThrownBy(() -> inOneTransaction(() -> {
            raiseCourseSectionCapacity(seeded.sectionId());
            eventPublisher.publishEvent(CourseSectionCapacityChangedEvent.of(seeded.sectionId(), 1, 2));
        })).hasMessageContaining("waitlist promotion failed");

        assertThat(capacityOf("course_section", seeded.sectionId())).isEqualTo(1);
        assertThat(statusOf("course_section_enrollment", seeded.waitingEnrollmentId())).isEqualTo("WAITLISTED");
    }

    @Test
    void courseSectionCapacityIncreaseWhosePromotionSucceedsCommitsTheCapacityAndThePromotion() {
        SeededCourseSection seeded = seedCourseSectionWithOneWaitingStudent();

        inOneTransaction(() -> {
            raiseCourseSectionCapacity(seeded.sectionId());
            eventPublisher.publishEvent(CourseSectionCapacityChangedEvent.of(seeded.sectionId(), 1, 2));
        });

        assertThat(capacityOf("course_section", seeded.sectionId())).isEqualTo(2);
        assertThat(statusOf("course_section_enrollment", seeded.waitingEnrollmentId())).isEqualTo("ENROLLED");
    }

    @Test
    void courseCapacityIncreaseWhoseListenerFailsRevertsTheCapacityChange() {
        Long courseId = seedCourse();
        Long unknownCourseId = courseId + 1_000_000;

        assertThatThrownBy(() -> inOneTransaction(() -> {
            raiseCourseCapacity(courseId);
            eventPublisher.publishEvent(CourseCapacityChangedEvent.of(unknownCourseId, 1, 2));
        })).isInstanceOf(CourseNotFoundException.class);

        assertThat(capacityOf("course", courseId)).isEqualTo(1);
    }

    @Test
    void courseCapacityIncreaseWhoseListenerSucceedsCommitsTheCapacityChange() {
        Long courseId = seedCourse();

        inOneTransaction(() -> {
            raiseCourseCapacity(courseId);
            eventPublisher.publishEvent(CourseCapacityChangedEvent.of(courseId, 1, 2));
        });

        assertThat(capacityOf("course", courseId)).isEqualTo(2);
    }

    private void failPromotionsOn(String table) {
        jdbcTemplate.execute("""
                create or replace function fail_waitlist_promotion() returns trigger as $$
                begin
                    if old.status = 'WAITLISTED' and new.status = 'ENROLLED' then
                        raise exception 'waitlist promotion failed';
                    end if;
                    return new;
                end
                $$ language plpgsql
                """);
        jdbcTemplate.execute("drop trigger if exists fail_waitlist_promotion on " + table);
        jdbcTemplate.execute("create trigger fail_waitlist_promotion before update on " + table
                + " for each row execute function fail_waitlist_promotion()");
        sabotagedTable = table;
    }

    private void inOneTransaction(Runnable work) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> work.run());
    }

    private int capacityOf(String table, Long id) {
        return jdbcTemplate.queryForObject("select capacity from " + table + " where id = ?", Integer.class, id);
    }

    private String statusOf(String table, Long id) {
        return jdbcTemplate.queryForObject("select status from " + table + " where id = ?", String.class, id);
    }

    private void raiseProgramSectionCapacity(Long sectionId) {
        entityManager.find(SubjectSection.class, sectionId).setCapacity(2);
        entityManager.flush();
    }

    private void raiseCourseSectionCapacity(Long sectionId) {
        entityManager.find(CourseSubjectSection.class, sectionId).setCapacity(2);
        entityManager.flush();
    }

    private void raiseCourseCapacity(Long courseId) {
        entityManager.find(Course.class, courseId).setCapacity(2);
        entityManager.flush();
    }

    private SeededProgramSection seedProgramSectionWithOneWaitingStudent() {
        SeededProgramSection[] seeded = new SeededProgramSection[1];
        inOneTransaction(() -> {
            CycleTestFixtures fixtures = new CycleTestFixtures(entityManager);
            Program program = fixtures.program();
            Term term = openTerm(fixtures);
            AcademicOfferingCycle cycle = fixtures.programCycle(program, term, AcademicOfferingCycleStatus.APPROVED);
            SubjectSection section = fixtures.section(fixtures.subject(), term, StatusType.ACTIVE, cycle);
            section.setCapacity(1);
            Student student = student(fixtures);
            ProgramEnrollment programEnrollment = new ProgramEnrollment();
            programEnrollment.setStudent(student);
            programEnrollment.setProgram(program);
            programEnrollment.setStatus(ProgramEnrollmentStatus.ENROLLED);
            programEnrollment.setEnrollmentDate(LocalDateTime.now().minusDays(30));
            entityManager.persist(programEnrollment);
            SubjectSectionEnrollment waiting = new SubjectSectionEnrollment();
            waiting.setProgramEnrollment(programEnrollment);
            waiting.setStudent(student);
            waiting.setSubjectSection(section);
            waiting.setStatus(EnrollmentStatus.WAITLISTED);
            waiting.setEnrolledAt(LocalDateTime.now().minusDays(5));
            waiting.setAttemptNumber(1);
            entityManager.persist(waiting);
            entityManager.flush();
            seeded[0] = new SeededProgramSection(section.getId(), waiting.getId());
        });
        return seeded[0];
    }

    private SeededCourseSection seedCourseSectionWithOneWaitingStudent() {
        SeededCourseSection[] seeded = new SeededCourseSection[1];
        inOneTransaction(() -> {
            CycleTestFixtures fixtures = new CycleTestFixtures(entityManager);
            Course course = course(fixtures);
            CourseSubject module = new CourseSubject();
            module.setCourse(course);
            module.setName("Module " + course.getName());
            module.setStatus(StatusType.ACTIVE);
            entityManager.persist(module);
            CourseSubjectSection section = new CourseSubjectSection();
            section.setName("Section " + course.getName());
            section.setCourseSubject(module);
            section.setTerm(course.getTerm());
            section.setCapacity(1);
            section.setStatus(StatusType.ACTIVE);
            entityManager.persist(section);
            CourseEnrollment parent = courseEnrollment(fixtures, course, EnrollmentStatus.ENROLLED);
            CourseSubjectSectionEnrollment waiting = new CourseSubjectSectionEnrollment();
            waiting.setStudent(parent.getStudent());
            waiting.setCourseEnrollment(parent);
            waiting.setCourseSubjectSection(section);
            waiting.setStatus(EnrollmentStatus.WAITLISTED);
            waiting.setEnrolledAt(LocalDateTime.now().minusDays(5));
            waiting.setAttemptNumber(1);
            entityManager.persist(waiting);
            entityManager.flush();
            seeded[0] = new SeededCourseSection(section.getId(), waiting.getId());
        });
        return seeded[0];
    }

    private Long seedCourse() {
        Long[] courseId = new Long[1];
        inOneTransaction(() -> {
            Course course = course(new CycleTestFixtures(entityManager));
            entityManager.flush();
            courseId[0] = course.getId();
        });
        return courseId[0];
    }

    private Term openTerm(CycleTestFixtures fixtures) {
        Term term = fixtures.term((int) YEARS.incrementAndGet());
        term.setEnrollmentEnd(LONG_AFTER_ANY_TEST);
        return term;
    }

    private Course course(CycleTestFixtures fixtures) {
        Course course = new Course();
        course.setName("Course " + YEARS.get());
        course.setTerm(openTerm(fixtures));
        course.setCapacity(1);
        course.setIsActive(StatusType.ACTIVE);
        course.setTeachingMode(TeachingMode.ONLINE);
        entityManager.persist(course);
        return course;
    }

    private CourseEnrollment courseEnrollment(CycleTestFixtures fixtures, Course course, EnrollmentStatus status) {
        CourseEnrollment enrollment = new CourseEnrollment();
        enrollment.setStudent(student(fixtures));
        enrollment.setCourse(course);
        enrollment.setStatus(status);
        enrollment.setEnrollmentDate(LocalDateTime.now().minusDays(8));
        entityManager.persist(enrollment);
        return enrollment;
    }

    private Student student(CycleTestFixtures fixtures) {
        Institution institution = new Institution();
        institution.setName("Test institute");
        entityManager.persist(institution);
        Student student = new Student();
        student.setPerson(fixtures.person());
        student.setInstitution(institution);
        student.setOnboardingStatus(StudentOnboardingStatus.CONFIRMADO);
        entityManager.persist(student);
        return student;
    }

    private record SeededProgramSection(Long sectionId, Long waitingEnrollmentId) {
    }

    private record SeededCourseSection(Long sectionId, Long waitingEnrollmentId) {
    }
}
