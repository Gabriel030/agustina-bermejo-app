package com.red_escuela.backend.enrollment.service;

import com.red_escuela.backend.academicstructure.CycleTestFixtures;
import com.red_escuela.backend.academicstructure.entity.AcademicOfferingCycle;
import com.red_escuela.backend.academicstructure.entity.AcademicOfferingCycleStatus;
import com.red_escuela.backend.academicstructure.entity.Course;
import com.red_escuela.backend.academicstructure.entity.CourseSubject;
import com.red_escuela.backend.academicstructure.entity.CourseSubjectSection;
import com.red_escuela.backend.academicstructure.entity.Program;
import com.red_escuela.backend.academicstructure.entity.Subject;
import com.red_escuela.backend.academicstructure.entity.SubjectSection;
import com.red_escuela.backend.academicstructure.entity.TeachingMode;
import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.academicstructure.entity.TermEnrollmentOverride;
import com.red_escuela.backend.academicstructure.service.CapacityChangeEvents;
import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.enrollment.dto.WaitlistKind;
import com.red_escuela.backend.enrollment.dto.WaitlistQueueSummaryResponse;
import com.red_escuela.backend.enrollment.dto.WaitlistVerdict;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class WaitlistSectionExpiryIT extends AbstractIntegrationTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2063, 4, 10, 9, 0);
    private static final Clock FIXED_CLOCK = Clock.fixed(NOW.toInstant(ZoneOffset.UTC), ZoneId.of("UTC"));

    @MockitoBean
    private WaitlistCandidateEligibility eligibility;

    @Autowired
    private EntityManager entityManager;
    @Autowired
    private WaitlistQueues waitlistQueues;

    @BeforeEach
    void everyoneIsEligibleByDefault() {
        when(eligibility.evaluate(any())).thenReturn(WaitlistVerdict.passing());
    }

    @Test
    void programSectionQueueShowsEnrollmentClose() {
        SubjectSectionEnrollment waiting = programWaiting(2063, NOW.plusDays(5));
        entityManager.flush();

        assertThat(nextExpiryOf(WaitlistKind.PROGRAM_SECTION, waiting.getSubjectSection().getId(), null))
                .isEqualTo(LocalDate.of(2063, 4, 15));
    }

    @Test
    void courseSectionQueueShowsEnrollmentClose() {
        CourseSubjectSection section = courseSectionQueue(2064, NOW.plusDays(6));
        entityManager.flush();

        assertThat(nextExpiryOf(WaitlistKind.COURSE_SECTION, section.getId(), null)).isEqualTo(LocalDate.of(2063, 4, 16));
    }

    @Test
    void overrideWins() {
        SubjectSectionEnrollment program = programWaiting(2065, NOW.plusDays(5));
        program.getSubjectSection().getTerm().setEnrollmentOverride(TermEnrollmentOverride.OPEN);
        CourseSubjectSection module = courseSectionQueue(2066, NOW.plusDays(5));
        module.getTerm().setEnrollmentOverride(TermEnrollmentOverride.OPEN);
        entityManager.flush();

        assertThat(nextExpiryOf(WaitlistKind.PROGRAM_SECTION, program.getSubjectSection().getId(), null)).isNull();
        assertThat(nextExpiryOf(WaitlistKind.COURSE_SECTION, module.getId(), null)).isNull();
        assertThat(idsExpiringWithin(WaitlistKind.PROGRAM_SECTION, 30)).doesNotContain(program.getSubjectSection().getId());
        assertThat(idsExpiringWithin(WaitlistKind.COURSE_SECTION, 30)).doesNotContain(module.getId());
    }

    @Test
    void closedOverrideStillShowsEnrollmentClose() {
        SubjectSectionEnrollment program = programWaiting(2075, NOW.plusDays(5));
        program.getSubjectSection().getTerm().setEnrollmentOverride(TermEnrollmentOverride.CLOSED);
        CourseSubjectSection module = courseSectionQueue(2076, NOW.plusDays(6));
        module.getTerm().setEnrollmentOverride(TermEnrollmentOverride.CLOSED);
        entityManager.flush();

        assertThat(nextExpiryOf(WaitlistKind.PROGRAM_SECTION, program.getSubjectSection().getId(), null))
                .isEqualTo(LocalDate.of(2063, 4, 15));
        assertThat(nextExpiryOf(WaitlistKind.COURSE_SECTION, module.getId(), null)).isEqualTo(LocalDate.of(2063, 4, 16));
        assertThat(idsExpiringWithin(WaitlistKind.PROGRAM_SECTION, 7)).contains(program.getSubjectSection().getId());
    }

    @Test
    void expiringFilterIncludesSectionQueues() {
        SubjectSectionEnrollment programSoon = programWaiting(2067, NOW.plusDays(5));
        SubjectSectionEnrollment programLater = programWaiting(2068, NOW.plusDays(20));
        CourseSubjectSection moduleSoon = courseSectionQueue(2069, NOW.plusDays(5));
        CourseSubjectSection moduleLater = courseSectionQueue(2070, NOW.plusDays(20));
        entityManager.flush();

        assertThat(idsExpiringWithin(WaitlistKind.PROGRAM_SECTION, 7))
                .contains(programSoon.getSubjectSection().getId()).doesNotContain(programLater.getSubjectSection().getId());
        assertThat(idsExpiringWithin(WaitlistKind.COURSE_SECTION, 7))
                .contains(moduleSoon.getId()).doesNotContain(moduleLater.getId());
    }

    private LocalDate nextExpiryOf(WaitlistKind kind, Long queueId, Integer expiringWithinDays) {
        return listed(kind, expiringWithinDays).stream()
                .filter(summary -> summary.id().equals(queueId))
                .findFirst().orElseThrow()
                .nextExpiry();
    }

    private List<Long> idsExpiringWithin(WaitlistKind kind, int days) {
        return listed(kind, days).stream().map(WaitlistQueueSummaryResponse::id).toList();
    }

    private List<WaitlistQueueSummaryResponse> listed(WaitlistKind kind, Integer expiringWithinDays) {
        return new WaitlistQueueLister(waitlistQueues, FIXED_CLOCK).list(kind, null, expiringWithinDays, 0, 100).items();
    }

    private CourseSubjectSection courseSectionQueue(int year, LocalDateTime enrollmentEnd) {
        Course course = course(year, 1);
        course.getTerm().setEnrollmentEnd(enrollmentEnd);
        CourseSubjectSection section = moduleSection(course);
        CourseEnrollment parent = courseEnrollment(course, EnrollmentStatus.WAITLISTED, NOW.minusDays(5), null);
        moduleEnrollment(section, parent, EnrollmentStatus.WAITLISTED);
        return section;
    }

    private SubjectSectionEnrollment programWaiting(int year, LocalDateTime enrollmentEnd) {
        CycleTestFixtures fixtures = new CycleTestFixtures(entityManager);
        Program program = fixtures.program();
        Term term = fixtures.term(year);
        term.setEnrollmentEnd(enrollmentEnd);
        Subject subject = fixtures.subject();
        AcademicOfferingCycle cycle = fixtures.programCycle(program, term, AcademicOfferingCycleStatus.APPROVED);
        SubjectSection section = fixtures.section(subject, term, StatusType.ACTIVE, cycle);
        section.setCapacity(1);
        return programEntry(section, program, EnrollmentStatus.WAITLISTED);
    }

    private SubjectSectionEnrollment programEntry(SubjectSection section, Program program, EnrollmentStatus status) {
        Student student = student();
        ProgramEnrollment programEnrollment = new ProgramEnrollment();
        programEnrollment.setStudent(student);
        programEnrollment.setProgram(program);
        programEnrollment.setStatus(ProgramEnrollmentStatus.ENROLLED);
        programEnrollment.setEnrollmentDate(NOW.minusDays(30));
        entityManager.persist(programEnrollment);
        SubjectSectionEnrollment enrollment = new SubjectSectionEnrollment();
        enrollment.setProgramEnrollment(programEnrollment);
        enrollment.setStudent(student);
        enrollment.setSubjectSection(section);
        enrollment.setStatus(status);
        enrollment.setEnrolledAt(NOW.minusDays(5));
        enrollment.setAttemptNumber(1);
        entityManager.persist(enrollment);
        return enrollment;
    }

    private Course course(int year, int capacity) {
        Term term = new CycleTestFixtures(entityManager).term(year);
        Course course = new Course();
        course.setName("Curso " + year);
        course.setTerm(term);
        course.setCapacity(capacity);
        course.setIsActive(StatusType.ACTIVE);
        course.setTeachingMode(TeachingMode.ONLINE);
        entityManager.persist(course);
        return course;
    }

    private CourseSubjectSection moduleSection(Course course) {
        CourseSubject module = new CourseSubject();
        module.setCourse(course);
        module.setName("Módulo " + course.getName());
        module.setStatus(StatusType.ACTIVE);
        entityManager.persist(module);
        CourseSubjectSection section = new CourseSubjectSection();
        section.setName("Comisión " + course.getName());
        section.setCourseSubject(module);
        section.setTerm(course.getTerm());
        section.setCapacity(1);
        section.setStatus(StatusType.ACTIVE);
        entityManager.persist(section);
        return section;
    }

    private Student student() {
        Institution institution = new Institution();
        institution.setName("Instituto");
        entityManager.persist(institution);
        Student student = new Student();
        student.setPerson(new CycleTestFixtures(entityManager).person());
        student.setInstitution(institution);
        student.setOnboardingStatus(StudentOnboardingStatus.CONFIRMADO);
        entityManager.persist(student);
        return student;
    }

    private CourseEnrollment courseEnrollment(Course course, EnrollmentStatus status, LocalDateTime joinedAt, LocalDateTime prioritizedAt) {
        CourseEnrollment enrollment = new CourseEnrollment();
        enrollment.setStudent(student());
        enrollment.setCourse(course);
        enrollment.setStatus(status);
        enrollment.setEnrollmentDate(joinedAt);
        enrollment.setWaitlistPrioritizedAt(prioritizedAt);
        entityManager.persist(enrollment);
        return enrollment;
    }

    private CourseSubjectSectionEnrollment moduleEnrollment(CourseSubjectSection section, CourseEnrollment parent, EnrollmentStatus status) {
        CourseSubjectSectionEnrollment enrollment = new CourseSubjectSectionEnrollment();
        enrollment.setStudent(parent.getStudent());
        enrollment.setCourseEnrollment(parent);
        enrollment.setCourseSubjectSection(section);
        enrollment.setStatus(status);
        enrollment.setEnrolledAt(NOW.minusDays(5));
        enrollment.setAttemptNumber(1);
        entityManager.persist(enrollment);
        return enrollment;
    }
}
