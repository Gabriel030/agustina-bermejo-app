package com.red_escuela.backend.enrollment.service;

import com.red_escuela.backend.academicstructure.CycleTestFixtures;
import com.red_escuela.backend.academicstructure.entity.Course;
import com.red_escuela.backend.academicstructure.entity.CourseSubject;
import com.red_escuela.backend.academicstructure.entity.CourseSubjectSection;
import com.red_escuela.backend.academicstructure.entity.TeachingMode;
import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.enrollment.dto.WaitlistKind;
import com.red_escuela.backend.enrollment.entity.CourseEnrollment;
import com.red_escuela.backend.enrollment.entity.CourseSubjectSectionEnrollment;
import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.enrollment.exception.WaitlistEntryNotWaitingException;
import com.red_escuela.backend.enrollment.exception.WaitlistNoFreeSeatException;
import com.red_escuela.backend.identity.entity.Institution;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.identity.entity.StudentOnboardingStatus;
import com.red_escuela.backend.shared.domain.StatusType;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class WaitlistActionsKindsIT extends AbstractIntegrationTest {

    private static final LocalDateTime BASE_TIME = LocalDateTime.of(2057, 3, 10, 9, 0);
    private static final String REASON = "Motivo suficiente";

    @Autowired
    private EntityManager entityManager;
    @Autowired
    private WaitlistEntryActions actions;
    @Autowired
    private WaitlistQueues waitlistQueues;

    private long auditCount(String action, Long entityId) {
        return ((Number) entityManager.createNativeQuery("select count(*) from audit_log where action = :a and entity_id = :id")
                .setParameter("a", action).setParameter("id", entityId).getSingleResult()).longValue();
    }

    private List<Long> waitingOrder(WaitlistKind kind, Long queueId) {
        entityManager.flush();
        return waitlistQueues.of(kind).findWaitingCandidates(queueId).stream().map(WaitlistCandidate::enrollmentId).toList();
    }

    @Test
    void courseRemoveWithdrawsTheEntryAndOnlyItsWaitlistedModules() {
        Course course = course(2057, 2);
        courseEnrollment(course, EnrollmentStatus.ENROLLED, BASE_TIME);
        CourseEnrollment entry = courseEnrollment(course, EnrollmentStatus.WAITLISTED, BASE_TIME.plusHours(1));
        CourseSubjectSection section = moduleSection(course);
        CourseSubjectSectionEnrollment waitingModule = moduleEnrollment(section, entry, EnrollmentStatus.WAITLISTED, BASE_TIME);
        CourseSubjectSectionEnrollment enrolledModule = moduleEnrollment(section, entry, EnrollmentStatus.ENROLLED, BASE_TIME);
        entityManager.flush();

        actions.remove(WaitlistKind.COURSE, entry.getId(), REASON);
        entityManager.flush();

        assertThat(entry.getStatus()).isEqualTo(EnrollmentStatus.WITHDRAWN);
        assertThat(waitingModule.getStatus()).isEqualTo(EnrollmentStatus.WITHDRAWN);
        assertThat(enrolledModule.getStatus()).isEqualTo(EnrollmentStatus.ENROLLED);
        assertThat(auditCount("WAITLIST_REMOVED_BY_STAFF", entry.getId())).isEqualTo(1);
    }

    @Test
    void coursePromoteWithoutFreeSeatFailsAndKeepsTheEntryWaiting() {
        Course course = course(2059, 1);
        courseEnrollment(course, EnrollmentStatus.ENROLLED, BASE_TIME);
        CourseEnrollment entry = courseEnrollment(course, EnrollmentStatus.WAITLISTED, BASE_TIME.plusHours(1));
        entityManager.flush();

        assertThatThrownBy(() -> actions.promote(WaitlistKind.COURSE, entry.getId())).isInstanceOf(WaitlistNoFreeSeatException.class);

        assertThat(entry.getStatus()).isEqualTo(EnrollmentStatus.WAITLISTED);
        assertThat(auditCount("WAITLIST_PROMOTED_BY_STAFF", entry.getId())).isZero();
    }

    @Test
    void courseLastPrioritizedEntryIsFirstAndTheSameEntryIsNotReprioritized() {
        Course course = course(2061, 1);
        courseEnrollment(course, EnrollmentStatus.ENROLLED, BASE_TIME);
        CourseEnrollment older = courseEnrollment(course, EnrollmentStatus.WAITLISTED, BASE_TIME.plusHours(1));
        CourseEnrollment newer = courseEnrollment(course, EnrollmentStatus.WAITLISTED, BASE_TIME.plusHours(2));
        entityManager.flush();

        actions.prioritize(WaitlistKind.COURSE, older.getId(), REASON);
        actions.prioritize(WaitlistKind.COURSE, newer.getId(), REASON);
        assertThat(waitingOrder(WaitlistKind.COURSE, course.getId())).containsExactly(newer.getId(), older.getId());

        LocalDateTime stamp = newer.getWaitlistPrioritizedAt();
        actions.prioritize(WaitlistKind.COURSE, newer.getId(), REASON);
        assertThat(newer.getWaitlistPrioritizedAt()).isEqualTo(stamp);
        assertThat(auditCount("WAITLIST_PRIORITIZED_BY_STAFF", newer.getId())).isEqualTo(1);

        actions.prioritize(WaitlistKind.COURSE, older.getId(), REASON);
        assertThat(waitingOrder(WaitlistKind.COURSE, course.getId())).containsExactly(older.getId(), newer.getId());
    }

    @Test
    void courseSectionPromoteEnrollsTheEntryAndDoublePromoteFails() {
        CourseSubjectSection section = moduleSection(course(2062, 2));
        CourseEnrollment parent = courseEnrollment(section.getCourseSubject().getCourse(), EnrollmentStatus.ENROLLED, BASE_TIME);
        moduleEnrollment(section, parent, EnrollmentStatus.ENROLLED, BASE_TIME);
        CourseSubjectSectionEnrollment entry = moduleEnrollment(section, parent, EnrollmentStatus.WAITLISTED, BASE_TIME.plusHours(1));
        entityManager.flush();

        actions.promote(WaitlistKind.COURSE_SECTION, entry.getId());
        entityManager.flush();

        assertThat(entry.getStatus()).isEqualTo(EnrollmentStatus.ENROLLED);
        assertThat(auditCount("WAITLIST_PROMOTED_BY_STAFF", entry.getId())).isEqualTo(1);
        assertThatThrownBy(() -> actions.promote(WaitlistKind.COURSE_SECTION, entry.getId())).isInstanceOf(WaitlistEntryNotWaitingException.class);
    }

    @Test
    void courseSectionPromoteWithoutFreeSeatFails() {
        CourseSubjectSection section = moduleSection(course(2063, 2));
        section.setCapacity(1);
        CourseEnrollment parent = courseEnrollment(section.getCourseSubject().getCourse(), EnrollmentStatus.ENROLLED, BASE_TIME);
        moduleEnrollment(section, parent, EnrollmentStatus.ENROLLED, BASE_TIME);
        CourseSubjectSectionEnrollment entry = moduleEnrollment(section, parent, EnrollmentStatus.WAITLISTED, BASE_TIME.plusHours(1));
        entityManager.flush();

        assertThatThrownBy(() -> actions.promote(WaitlistKind.COURSE_SECTION, entry.getId())).isInstanceOf(WaitlistNoFreeSeatException.class);
        assertThat(entry.getStatus()).isEqualTo(EnrollmentStatus.WAITLISTED);
    }

    @Test
    void courseSectionRemoveWithdrawsTheEntryAndAudits() {
        CourseSubjectSection section = moduleSection(course(2064, 2));
        CourseEnrollment parent = courseEnrollment(section.getCourseSubject().getCourse(), EnrollmentStatus.ENROLLED, BASE_TIME);
        CourseSubjectSectionEnrollment entry = moduleEnrollment(section, parent, EnrollmentStatus.WAITLISTED, BASE_TIME);
        entityManager.flush();

        actions.remove(WaitlistKind.COURSE_SECTION, entry.getId(), REASON);
        entityManager.flush();

        assertThat(entry.getStatus()).isEqualTo(EnrollmentStatus.WITHDRAWN);
        assertThat(auditCount("WAITLIST_REMOVED_BY_STAFF", entry.getId())).isEqualTo(1);
        assertThatThrownBy(() -> actions.remove(WaitlistKind.COURSE_SECTION, entry.getId(), REASON)).isInstanceOf(WaitlistEntryNotWaitingException.class);
    }

    @Test
    void courseSectionLastPrioritizedEntryIsFirst() {
        CourseSubjectSection section = moduleSection(course(2065, 2));
        CourseEnrollment parent = courseEnrollment(section.getCourseSubject().getCourse(), EnrollmentStatus.ENROLLED, BASE_TIME);
        CourseSubjectSectionEnrollment older = moduleEnrollment(section, parent, EnrollmentStatus.WAITLISTED, BASE_TIME);
        CourseSubjectSectionEnrollment newer = moduleEnrollment(section, parent, EnrollmentStatus.WAITLISTED, BASE_TIME.plusHours(1));
        entityManager.flush();

        actions.prioritize(WaitlistKind.COURSE_SECTION, older.getId(), REASON);
        actions.prioritize(WaitlistKind.COURSE_SECTION, newer.getId(), REASON);

        assertThat(waitingOrder(WaitlistKind.COURSE_SECTION, section.getId())).containsExactly(newer.getId(), older.getId());
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
        section.setCapacity(2);
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

    private CourseEnrollment courseEnrollment(Course course, EnrollmentStatus status, LocalDateTime joinedAt) {
        CourseEnrollment enrollment = new CourseEnrollment();
        enrollment.setStudent(student());
        enrollment.setCourse(course);
        enrollment.setStatus(status);
        enrollment.setEnrollmentDate(joinedAt);
        entityManager.persist(enrollment);
        return enrollment;
    }

    private CourseSubjectSectionEnrollment moduleEnrollment(CourseSubjectSection section, CourseEnrollment parent,
                                                            EnrollmentStatus status, LocalDateTime joinedAt) {
        CourseSubjectSectionEnrollment enrollment = new CourseSubjectSectionEnrollment();
        enrollment.setStudent(student());
        enrollment.setCourseEnrollment(parent);
        enrollment.setCourseSubjectSection(section);
        enrollment.setStatus(status);
        enrollment.setEnrolledAt(joinedAt);
        enrollment.setAttemptNumber(1);
        entityManager.persist(enrollment);
        return enrollment;
    }
}
