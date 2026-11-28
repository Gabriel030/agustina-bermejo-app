package com.red_escuela.backend.enrollment.service;

import com.red_escuela.backend.academicstructure.CycleTestFixtures;
import com.red_escuela.backend.academicstructure.api.CourseCatalogFinder;
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
import com.red_escuela.backend.academicstructure.dto.UpdateCourseRequest;
import com.red_escuela.backend.academicstructure.dto.UpdateSectionRequest;
import com.red_escuela.backend.academicstructure.entity.TermEnrollmentOverride;
import com.red_escuela.backend.academicstructure.service.CapacityChangeEvents;
import com.red_escuela.backend.academicstructure.service.CourseSectionService;
import com.red_escuela.backend.academicstructure.service.CourseService;
import com.red_escuela.backend.academicstructure.service.SubjectSectionService;
import com.red_escuela.backend.enrollment.events.WaitlistEntryExpiredEvent;
import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.enrollment.dto.WaitlistVerdict;
import com.red_escuela.backend.enrollment.dto.WaitlistVerdictReason;
import com.red_escuela.backend.enrollment.entity.CourseEnrollment;
import com.red_escuela.backend.enrollment.entity.CourseSubjectSectionEnrollment;
import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentStatus;
import com.red_escuela.backend.enrollment.entity.SubjectSectionEnrollment;
import com.red_escuela.backend.enrollment.events.WaitlistExpiryReminderEvent;
import com.red_escuela.backend.identity.entity.Institution;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.identity.entity.StudentOnboardingStatus;
import com.red_escuela.backend.shared.domain.StatusType;
import jakarta.persistence.EntityManager;
import com.red_escuela.backend.testsupport.NightlyTest;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@RecordApplicationEvents
class WaitlistExpiryAndCapacityIT extends AbstractIntegrationTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2061, 4, 10, 9, 0);
    private static final Clock FIXED_CLOCK = Clock.fixed(NOW.toInstant(ZoneOffset.UTC), ZoneId.of("UTC"));

    @MockitoBean
    private WaitlistCandidateEligibility eligibility;

    @Autowired
    private EntityManager entityManager;
    @Autowired
    private EntityManagerFactory entityManagerFactory;
    @Autowired
    private CapacityChangeEvents capacityChangeEvents;
    @Autowired
    private SectionWaitlistExpiration sectionExpiration;
    @Autowired
    private WaitlistExpiryReminder reminder;
    @Autowired
    private CourseCatalogFinder courseCatalogFinder;
    @Autowired
    private CourseWaitlistExpiration courseWaitlistExpiration;
    @Autowired
    private ApplicationEvents applicationEvents;
    @Autowired
    private CourseService courseService;
    @Autowired
    private SubjectSectionService subjectSectionService;
    @Autowired
    private CourseSectionService courseSectionService;

    @BeforeEach
    void everyoneIsEligibleByDefault() {
        when(eligibility.evaluate(any())).thenReturn(WaitlistVerdict.passing());
    }

    @Test
    void capacityIncreasePromotesInOrder() {
        Course course = course(2061, 1);
        courseEnrollment(course, EnrollmentStatus.ENROLLED, NOW, null);
        CourseEnrollment oldest = courseEnrollment(course, EnrollmentStatus.WAITLISTED, NOW.plusDays(1), null);
        CourseEnrollment middle = courseEnrollment(course, EnrollmentStatus.WAITLISTED, NOW.plusDays(2), null);
        CourseEnrollment prioritized = courseEnrollment(course, EnrollmentStatus.WAITLISTED, NOW.plusDays(4), NOW.plusDays(5));
        entityManager.flush();

        course.setCapacity(3);
        capacityChangeEvents.courseChanged(course.getId(), 1, 3);

        assertThat(prioritized.getStatus()).isEqualTo(EnrollmentStatus.ENROLLED);
        assertThat(oldest.getStatus()).isEqualTo(EnrollmentStatus.ENROLLED);
        assertThat(middle.getStatus()).isEqualTo(EnrollmentStatus.WAITLISTED);
    }

    @Test
    @NightlyTest(reason = "Persists 400 waitlisted students to count the promotion statements")
    void promotionStatementCountStaysConstantWithFourHundredWaitlisted() {
        long fewWaiting = promotionStatements(2091, 5);
        long manyWaiting = promotionStatements(2092, 400);

        assertThat(manyWaiting).isEqualTo(fewWaiting);
    }

    @Test
    void capacityIncreaseSkipsIneligibleCandidateAndKeepsItsPlace() {
        Course course = course(2062, 1);
        courseEnrollment(course, EnrollmentStatus.ENROLLED, NOW, null);
        CourseEnrollment blocked = courseEnrollment(course, EnrollmentStatus.WAITLISTED, NOW.plusDays(1), null);
        CourseEnrollment eligible = courseEnrollment(course, EnrollmentStatus.WAITLISTED, NOW.plusDays(2), null);
        entityManager.flush();
        when(eligibility.evaluate(any())).thenAnswer(invocation -> {
            WaitlistCandidate candidate = invocation.getArgument(0);
            return candidate.enrollmentId().equals(blocked.getId())
                    ? WaitlistVerdict.skippedBecause(WaitlistVerdictReason.ADMISSION_RECEIPT_PENDING)
                    : WaitlistVerdict.passing();
        });

        course.setCapacity(2);
        capacityChangeEvents.courseChanged(course.getId(), 1, 2);

        assertThat(eligible.getStatus()).isEqualTo(EnrollmentStatus.ENROLLED);
        assertThat(blocked.getStatus()).isEqualTo(EnrollmentStatus.WAITLISTED);
    }

    @Test
    void capacityDecreaseDoesNotPromote() {
        Course course = course(2063, 2);
        courseEnrollment(course, EnrollmentStatus.ENROLLED, NOW, null);
        CourseEnrollment waiting = courseEnrollment(course, EnrollmentStatus.WAITLISTED, NOW.plusDays(1), null);
        entityManager.flush();

        capacityChangeEvents.courseChanged(course.getId(), 3, 2);

        assertThat(waiting.getStatus()).isEqualTo(EnrollmentStatus.WAITLISTED);
    }

    @Test
    void capacityListenerRunsSynchronouslyInsideTheCallerTransaction() {
        Course course = course(2064, 1);
        courseEnrollment(course, EnrollmentStatus.ENROLLED, NOW, null);
        CourseEnrollment waiting = courseEnrollment(course, EnrollmentStatus.WAITLISTED, NOW.plusDays(1), null);
        entityManager.flush();
        course.setCapacity(2);

        capacityChangeEvents.courseChanged(course.getId(), 1, 2);

        assertThat(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
        assertThat(waiting.getStatus()).isEqualTo(EnrollmentStatus.ENROLLED);
    }

    @Test
    void noDuplicateReminderOnSecondRun() {
        SubjectSectionEnrollment waiting = programWaiting(2065, NOW.plusDays(2));
        entityManager.flush();
        WaitlistExpiryJob job = new WaitlistExpiryJob(sectionExpiration, reminder, FIXED_CLOCK);

        job.sendExpiryReminders();
        job.sendExpiryReminders();

        assertThat(applicationEvents.stream(WaitlistExpiryReminderEvent.class)
                .filter(event -> event.enrollmentId().equals(waiting.getId()))).hasSize(1);
        assertThat(waiting.getWaitlistExpiryRemindedAt()).isNotNull();
    }

    @Test
    void reminderIsNotSentWhenClosingIsFartherThanThreeDays() {
        SubjectSectionEnrollment waiting = programWaiting(2066, NOW.plusDays(4));
        entityManager.flush();

        new WaitlistExpiryJob(sectionExpiration, reminder, FIXED_CLOCK).sendExpiryReminders();

        assertThat(applicationEvents.stream(WaitlistExpiryReminderEvent.class)
                .filter(event -> event.enrollmentId().equals(waiting.getId()))).isEmpty();
    }

    @Test
    void expiryClosesAllThreeQueues() {
        SubjectSectionEnrollment programWaiting = programWaiting(2067, NOW.minusDays(1));
        Course course = course(2068, 1);
        course.setWaitlistDeadlineOffsetDays(0);
        course.getTerm().setStartDate(NOW.minusDays(1));
        CourseEnrollment courseWaiting = courseEnrollment(course, EnrollmentStatus.WAITLISTED, NOW.minusDays(10), null);
        Course moduleCourse = course(2069, 1);
        moduleCourse.getTerm().setEnrollmentEnd(NOW.minusDays(1));
        CourseSubjectSection section = moduleSection(moduleCourse);
        CourseEnrollment parent = courseEnrollment(moduleCourse, EnrollmentStatus.ENROLLED, NOW.minusDays(10), null);
        CourseSubjectSectionEnrollment moduleWaiting = moduleEnrollment(section, parent, EnrollmentStatus.WAITLISTED);
        SubjectSectionEnrollment programEnrolled = programEntry(programWaiting.getSubjectSection(), programWaiting.getProgramEnrollment().getProgram(), EnrollmentStatus.ENROLLED);
        CourseSubjectSectionEnrollment moduleEnrolled = moduleEnrollment(moduleSection(moduleCourse), parent, EnrollmentStatus.ENROLLED);
        entityManager.flush();

        new CourseEnrollmentWaitlistExpiryJob(courseCatalogFinder, courseWaitlistExpiration, FIXED_CLOCK).expireWaitlists();
        new WaitlistExpiryJob(sectionExpiration, reminder, FIXED_CLOCK).expireSectionWaitlists();
        entityManager.flush();
        entityManager.clear();

        assertThat(statusOf(SubjectSectionEnrollment.class, programWaiting.getId())).isEqualTo(EnrollmentStatus.WAITLIST_EXPIRED);
        assertThat(statusOf(CourseEnrollment.class, courseWaiting.getId())).isEqualTo(EnrollmentStatus.WAITLIST_EXPIRED);
        assertThat(statusOf(CourseSubjectSectionEnrollment.class, moduleWaiting.getId())).isEqualTo(EnrollmentStatus.WAITLIST_EXPIRED);
        assertThat(statusOf(SubjectSectionEnrollment.class, programEnrolled.getId())).isEqualTo(EnrollmentStatus.ENROLLED);
        assertThat(statusOf(CourseSubjectSectionEnrollment.class, moduleEnrolled.getId())).isEqualTo(EnrollmentStatus.ENROLLED);
        Long audits = entityManager.createQuery("select count(a) from AuditLogEntry a where a.action = :action and a.entityId in :ids", Long.class)
                .setParameter("action", com.red_escuela.backend.shared.audit.AuditAction.WAITLIST_EXPIRED)
                .setParameter("ids", java.util.List.of(programWaiting.getId(), courseWaiting.getId(), moduleWaiting.getId()))
                .getSingleResult();
        assertThat(audits).isEqualTo(3L);
    }

    @Test
    void expiryLeavesQueuesStillOpenUntouched() {
        SubjectSectionEnrollment programWaiting = programWaiting(2070, NOW.plusDays(1));
        entityManager.flush();

        new WaitlistExpiryJob(sectionExpiration, reminder, FIXED_CLOCK).expireSectionWaitlists();

        assertThat(programWaiting.getStatus()).isEqualTo(EnrollmentStatus.WAITLISTED);
    }

    @Test
    void courseUpdateRaisingCapacityPromotesTheOldestWaitingEntry() {
        Course course = course(2071, 1);
        courseEnrollment(course, EnrollmentStatus.ENROLLED, NOW, null);
        CourseEnrollment first = courseEnrollment(course, EnrollmentStatus.WAITLISTED, NOW.plusDays(1), null);
        CourseEnrollment second = courseEnrollment(course, EnrollmentStatus.WAITLISTED, NOW.plusDays(2), null);
        entityManager.flush();

        courseService.update(course.getId(), new UpdateCourseRequest(null, null, 2, null, null, null, null, null));

        assertThat(first.getStatus()).isEqualTo(EnrollmentStatus.ENROLLED);
        assertThat(second.getStatus()).isEqualTo(EnrollmentStatus.WAITLISTED);
    }

    @Test
    void courseUpdateWithoutCapacityIncreaseDoesNotPromote() {
        Course course = course(2072, 1);
        courseEnrollment(course, EnrollmentStatus.ENROLLED, NOW, null);
        CourseEnrollment waiting = courseEnrollment(course, EnrollmentStatus.WAITLISTED, NOW.plusDays(1), null);
        entityManager.flush();

        courseService.update(course.getId(), new UpdateCourseRequest("Otro nombre", null, null, null, null, null, null, null));

        assertThat(waiting.getStatus()).isEqualTo(EnrollmentStatus.WAITLISTED);
    }

    @Test
    void programSectionUpdateRaisingCapacityPromotesTheWaitingEntry() {
        SubjectSectionEnrollment waiting = programWaiting(2073, NOW.plusDays(10));
        SubjectSection section = waiting.getSubjectSection();
        programEntry(section, waiting.getProgramEnrollment().getProgram(), EnrollmentStatus.ENROLLED);
        entityManager.flush();

        subjectSectionService.update(section.getId(), new UpdateSectionRequest(null, null, 2, null));

        assertThat(waiting.getStatus()).isEqualTo(EnrollmentStatus.ENROLLED);
    }

    @Test
    void courseSectionUpdateRaisingCapacityPromotesTheWaitingEntry() {
        Course course = course(2074, 5);
        CourseSubjectSection section = moduleSection(course);
        CourseEnrollment parent = courseEnrollment(course, EnrollmentStatus.ENROLLED, NOW.minusDays(10), null);
        moduleEnrollment(section, parent, EnrollmentStatus.ENROLLED);
        CourseSubjectSectionEnrollment waiting = moduleEnrollment(section, courseEnrollment(course, EnrollmentStatus.ENROLLED, NOW.minusDays(10), null), EnrollmentStatus.WAITLISTED);
        entityManager.flush();

        courseSectionService.update(section.getId(), new UpdateSectionRequest(null, null, 2, null));

        assertThat(waiting.getStatus()).isEqualTo(EnrollmentStatus.ENROLLED);
    }

    @Test
    void oneFreeSeatPromotesOnlyOneOfTwoCandidates() {
        Course course = course(2075, 1);
        courseEnrollment(course, EnrollmentStatus.ENROLLED, NOW, null);
        CourseEnrollment first = courseEnrollment(course, EnrollmentStatus.WAITLISTED, NOW.plusDays(1), null);
        CourseEnrollment second = courseEnrollment(course, EnrollmentStatus.WAITLISTED, NOW.plusDays(2), null);
        entityManager.flush();

        course.setCapacity(2);
        capacityChangeEvents.courseChanged(course.getId(), 1, 2);

        assertThat(first.getStatus()).isEqualTo(EnrollmentStatus.ENROLLED);
        assertThat(second.getStatus()).isEqualTo(EnrollmentStatus.WAITLISTED);
    }

    @Test
    void seatTakenByManualPromotionIsNotGivenAwayAgain() {
        Course course = course(2076, 1);
        courseEnrollment(course, EnrollmentStatus.ENROLLED, NOW, null);
        CourseEnrollment promotedManually = courseEnrollment(course, EnrollmentStatus.WAITLISTED, NOW.plusDays(1), null);
        CourseEnrollment waiting = courseEnrollment(course, EnrollmentStatus.WAITLISTED, NOW.plusDays(2), null);
        entityManager.flush();
        promotedManually.setStatus(EnrollmentStatus.ENROLLED);
        entityManager.flush();

        course.setCapacity(2);
        capacityChangeEvents.courseChanged(course.getId(), 1, 2);

        assertThat(waiting.getStatus()).isEqualTo(EnrollmentStatus.WAITLISTED);
    }

    @Test
    void failureWhilePromotingPropagatesToTheCapacityUpdate() {
        Course course = course(2077, 1);
        courseEnrollment(course, EnrollmentStatus.ENROLLED, NOW, null);
        courseEnrollment(course, EnrollmentStatus.WAITLISTED, NOW.plusDays(1), null);
        entityManager.flush();
        when(eligibility.evaluate(any())).thenThrow(new IllegalStateException("eligibility down"));

        assertThatThrownBy(() -> courseService.update(course.getId(), new UpdateCourseRequest(null, null, 2, null, null, null, null, null)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void expiryTwiceExpiresAndNotifiesOnlyOnce() {
        SubjectSectionEnrollment waiting = programWaiting(2078, NOW.minusDays(1));
        entityManager.flush();
        WaitlistExpiryJob job = new WaitlistExpiryJob(sectionExpiration, reminder, FIXED_CLOCK);

        job.expireSectionWaitlists();
        entityManager.flush();
        job.expireSectionWaitlists();
        entityManager.flush();

        assertThat(waiting.getStatus()).isEqualTo(EnrollmentStatus.WAITLIST_EXPIRED);
        assertThat(applicationEvents.stream(WaitlistEntryExpiredEvent.class)
                .filter(event -> event.enrollmentId().equals(waiting.getId()))).hasSize(1);
        Long audits = entityManager.createQuery("select count(a) from AuditLogEntry a where a.action = :action and a.entityId = :id", Long.class)
                .setParameter("action", com.red_escuela.backend.shared.audit.AuditAction.WAITLIST_EXPIRED)
                .setParameter("id", waiting.getId())
                .getSingleResult();
        assertThat(audits).isEqualTo(1L);
    }

    @Test
    void expiryKeepsEnrolledEntriesOfTheSameSection() {
        SubjectSectionEnrollment waiting = programWaiting(2079, NOW.minusDays(1));
        SubjectSectionEnrollment enrolled = programEntry(waiting.getSubjectSection(), waiting.getProgramEnrollment().getProgram(), EnrollmentStatus.ENROLLED);
        entityManager.flush();

        new WaitlistExpiryJob(sectionExpiration, reminder, FIXED_CLOCK).expireSectionWaitlists();

        assertThat(waiting.getStatus()).isEqualTo(EnrollmentStatus.WAITLIST_EXPIRED);
        assertThat(enrolled.getStatus()).isEqualTo(EnrollmentStatus.ENROLLED);
    }

    @Test
    void expiryDoesNotRunWhenEnrollmentIsForcedOpenAfterTheEndDate() {
        SubjectSectionEnrollment waiting = programWaiting(2080, NOW.minusDays(1));
        waiting.getSubjectSection().getTerm().setEnrollmentOverride(TermEnrollmentOverride.OPEN);
        entityManager.flush();

        new WaitlistExpiryJob(sectionExpiration, reminder, FIXED_CLOCK).expireSectionWaitlists();

        assertThat(waiting.getStatus()).isEqualTo(EnrollmentStatus.WAITLISTED);
        assertThat(applicationEvents.stream(WaitlistEntryExpiredEvent.class)).isEmpty();
    }

    @Test
    void expiryStillRunsWhenEnrollmentIsForcedClosedAfterTheEndDate() {
        SubjectSectionEnrollment waiting = programWaiting(2081, NOW.minusDays(1));
        waiting.getSubjectSection().getTerm().setEnrollmentOverride(TermEnrollmentOverride.CLOSED);
        entityManager.flush();

        new WaitlistExpiryJob(sectionExpiration, reminder, FIXED_CLOCK).expireSectionWaitlists();

        assertThat(waiting.getStatus()).isEqualTo(EnrollmentStatus.WAITLIST_EXPIRED);
    }

    @Test
    void reminderIsNotSentWhenEnrollmentIsForcedClosed() {
        SubjectSectionEnrollment waiting = programWaiting(2082, NOW.plusDays(2));
        waiting.getSubjectSection().getTerm().setEnrollmentOverride(TermEnrollmentOverride.CLOSED);
        entityManager.flush();

        new WaitlistExpiryJob(sectionExpiration, reminder, FIXED_CLOCK).sendExpiryReminders();

        assertThat(applicationEvents.stream(WaitlistExpiryReminderEvent.class)
                .filter(event -> event.enrollmentId().equals(waiting.getId()))).isEmpty();
    }

    @Test
    void expiryAbortsWhenTheWindowWasExtendedAfterSelectingTheSection() {
        SubjectSectionEnrollment waiting = programWaiting(2083, NOW.plusDays(5));
        entityManager.flush();

        sectionExpiration.expireProgramSection(waiting.getSubjectSection().getId(), NOW);

        assertThat(waiting.getStatus()).isEqualTo(EnrollmentStatus.WAITLISTED);
    }

    @Test
    void courseSectionExpiryAbortsWhenTheWindowWasExtendedAfterSelectingTheSection() {
        Course course = course(2084, 1);
        course.getTerm().setEnrollmentEnd(NOW.plusDays(5));
        CourseSubjectSection section = moduleSection(course);
        CourseEnrollment parent = courseEnrollment(course, EnrollmentStatus.ENROLLED, NOW.minusDays(10), null);
        CourseSubjectSectionEnrollment waiting = moduleEnrollment(section, parent, EnrollmentStatus.WAITLISTED);
        entityManager.flush();

        sectionExpiration.expireCourseSection(section.getId(), NOW);

        assertThat(waiting.getStatus()).isEqualTo(EnrollmentStatus.WAITLISTED);
    }

    private <T> EnrollmentStatus statusOf(Class<T> type, Long id) {
        Object entity = entityManager.find(type, id);
        if (entity instanceof SubjectSectionEnrollment e) {
            return e.getStatus();
        }
        if (entity instanceof CourseEnrollment e) {
            return e.getStatus();
        }
        return ((CourseSubjectSectionEnrollment) entity).getStatus();
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

    private long promotionStatements(int year, int waiting) {
        Course course = course(year, 1);
        courseEnrollment(course, EnrollmentStatus.ENROLLED, NOW, null);
        for (int position = 1; position <= waiting; position++) {
            courseEnrollment(course, EnrollmentStatus.WAITLISTED, NOW.plusMinutes(position), null);
        }
        course.setCapacity(2);
        entityManager.flush();
        entityManager.clear();
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.setStatisticsEnabled(true);
        statistics.clear();
        capacityChangeEvents.courseChanged(course.getId(), 1, 2);
        long count = statistics.getPrepareStatementCount();
        statistics.setStatisticsEnabled(false);
        entityManager.clear();
        return count;
    }
}
