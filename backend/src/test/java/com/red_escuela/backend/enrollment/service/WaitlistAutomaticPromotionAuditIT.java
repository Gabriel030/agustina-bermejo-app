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
import com.red_escuela.backend.enrollment.dto.WaitlistVerdictReason;
import com.red_escuela.backend.enrollment.events.WaitlistEntryPausedEvent;
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
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import com.red_escuela.backend.shared.audit.AuditAction;
import com.red_escuela.backend.shared.audit.AuditLogEntry;
import com.red_escuela.backend.shared.audit.AuditLogRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@RecordApplicationEvents
class WaitlistAutomaticPromotionAuditIT extends AbstractIntegrationTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2071, 4, 10, 9, 0);

    @MockitoBean
    private WaitlistCandidateEligibility eligibility;

    @Autowired
    private ApplicationEvents applicationEvents;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private CapacityChangeEvents capacityChangeEvents;
    @Autowired
    private CourseSectionEnrollmentService courseSectionEnrollmentService;
    @Autowired
    private WaitlistEntryActions waitlistEntryActions;
    @Autowired
    private AuditLogRepository auditLogRepository;

    @BeforeEach
    void everyoneIsEligibleByDefault() {
        when(eligibility.evaluate(any())).thenReturn(WaitlistVerdict.passing());
    }

    @Test
    void capacityIncreaseIsAudited() {
        SubjectSectionEnrollment waiting = programWaiting(2071, NOW.plusDays(5));
        SubjectSection section = waiting.getSubjectSection();
        section.setCapacity(2);
        entityManager.flush();

        capacityChangeEvents.programSectionChanged(section.getId(), 1, 2);
        entityManager.flush();

        assertThat(waiting.getStatus()).isEqualTo(EnrollmentStatus.ENROLLED);
        assertThat(actionsOf(waiting.getId())).containsExactly(AuditAction.WAITLIST_PROMOTED_AUTOMATICALLY);
        AuditLogEntry entry = entriesOf(waiting.getId()).get(0);
        assertThat(entry.getBeforeValue()).isEqualTo("WAITLISTED");
        assertThat(entry.getAfterValue()).isEqualTo("ENROLLED");
    }

    @Test
    void entrySkippedByScheduleOverlapIsAnnouncedAsPausedWhenTheOneBehindIsPromoted() {
        SubjectSectionEnrollment paused = programWaiting(2074, NOW.plusDays(5));
        SubjectSection section = paused.getSubjectSection();
        SubjectSectionEnrollment promoted = programEntry(section, paused.getProgramEnrollment().getProgram(), EnrollmentStatus.WAITLISTED);
        promoted.setEnrolledAt(NOW.minusDays(4));
        section.setCapacity(2);
        entityManager.flush();
        when(eligibility.evaluate(any())).thenAnswer(invocation -> {
            WaitlistCandidate candidate = invocation.getArgument(0);
            return candidate.enrollmentId().equals(paused.getId())
                    ? WaitlistVerdict.skippedBecause(WaitlistVerdictReason.SCHEDULE_OVERLAP)
                    : WaitlistVerdict.passing();
        });
        when(eligibility.findConflictingSectionName(any())).thenReturn(Optional.of("Historia II"));

        capacityChangeEvents.programSectionChanged(section.getId(), 1, 2);
        entityManager.flush();

        assertThat(promoted.getStatus()).isEqualTo(EnrollmentStatus.ENROLLED);
        assertThat(paused.getStatus()).isEqualTo(EnrollmentStatus.WAITLISTED);
        List<WaitlistEntryPausedEvent> pausedEvents = applicationEvents.stream(WaitlistEntryPausedEvent.class).toList();
        assertThat(pausedEvents).hasSize(1);
        assertThat(pausedEvents.get(0).enrollmentId()).isEqualTo(paused.getId());
        assertThat(pausedEvents.get(0).conflictingSectionName()).isEqualTo("Historia II");
    }

    @Test
    void withdrawalPromotionIsAudited() {
        Course course = course(2072, 1);
        CourseSubjectSection section = moduleSection(course);
        CourseEnrollment parent = courseEnrollment(course, EnrollmentStatus.ENROLLED, NOW.minusDays(9), null);
        CourseSubjectSectionEnrollment seated = moduleEnrollment(section, parent, EnrollmentStatus.ENROLLED);
        CourseSubjectSectionEnrollment waiting = moduleEnrollment(section, courseEnrollment(course, EnrollmentStatus.ENROLLED, NOW.minusDays(8), null),
                EnrollmentStatus.WAITLISTED);
        entityManager.flush();

        Long withdrawingPersonId = seated.getStudent().getPerson().getId();
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken(withdrawingPersonId.toString(), null, "ROLE_ESTUDIANTE"));
        try {
            courseSectionEnrollmentService.withdraw(seated.getId(), EnrollmentChange.byStudent());
            entityManager.flush();
        } finally {
            SecurityContextHolder.clearContext();
        }

        assertThat(waiting.getStatus()).isEqualTo(EnrollmentStatus.ENROLLED);
        assertThat(actionsOf(waiting.getId())).containsExactly(AuditAction.WAITLIST_PROMOTED_AUTOMATICALLY);
        assertThat(entriesOf(waiting.getId()).get(0).getActorPersonId()).isEqualTo(withdrawingPersonId);
    }

    @Test
    void manualPromotionIsNotAuditedAsAutomatic() {
        SubjectSectionEnrollment waiting = programWaiting(2073, NOW.plusDays(5));
        waiting.getSubjectSection().setCapacity(2);
        entityManager.flush();

        waitlistEntryActions.promote(WaitlistKind.PROGRAM_SECTION, waiting.getId());
        entityManager.flush();

        assertThat(actionsOf(waiting.getId())).containsExactly(AuditAction.WAITLIST_PROMOTED_BY_STAFF);
    }

    private List<AuditLogEntry> entriesOf(Long enrollmentId) {
        return auditLogRepository.findAll().stream().filter(entry -> entry.getEntityId().equals(enrollmentId)).toList();
    }

    private List<AuditAction> actionsOf(Long enrollmentId) {
        return entriesOf(enrollmentId).stream().map(AuditLogEntry::getAction).toList();
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
