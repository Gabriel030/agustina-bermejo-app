package com.red_escuela.backend.enrollment.service;

import com.red_escuela.backend.academicstructure.CycleTestFixtures;
import com.red_escuela.backend.academicstructure.entity.Course;
import com.red_escuela.backend.academicstructure.entity.CourseSubject;
import com.red_escuela.backend.academicstructure.entity.CourseSubjectSection;
import com.red_escuela.backend.academicstructure.entity.TeachingMode;
import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.enrollment.dto.WaitlistKind;
import com.red_escuela.backend.enrollment.dto.WaitlistVerdict;
import com.red_escuela.backend.enrollment.dto.WaitlistVerdictReason;
import com.red_escuela.backend.enrollment.entity.CourseEnrollment;
import com.red_escuela.backend.enrollment.entity.CourseSubjectSectionEnrollment;
import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
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

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class WaitlistPriorityPromotionIT extends AbstractIntegrationTest {

    private static final LocalDateTime BASE_TIME = LocalDateTime.of(2053, 3, 10, 9, 0);

    @MockitoBean
    private WaitlistCandidateEligibility eligibility;

    @Autowired
    private EntityManager entityManager;
    @Autowired
    private WaitlistPromoter waitlistPromoter;

    @BeforeEach
    void everyoneIsEligibleByDefault() {
        when(eligibility.evaluate(any())).thenReturn(WaitlistVerdict.passing());
    }

    @Test
    void prioritizedCourseStudentIsPromotedBeforeTheOldestOne() {
        Course course = course(2053);
        CourseEnrollment older = courseEnrollment(course, EnrollmentStatus.WAITLISTED, BASE_TIME, null);
        CourseEnrollment prioritized = courseEnrollment(course, EnrollmentStatus.WAITLISTED, BASE_TIME.plusDays(1), BASE_TIME.plusDays(2));
        courseEnrollment(course, EnrollmentStatus.ENROLLED, BASE_TIME, null);
        entityManager.flush();

        waitlistPromoter.promoteNext(WaitlistKind.COURSE, course.getId());

        assertThat(prioritized.getStatus()).isEqualTo(EnrollmentStatus.ENROLLED);
        assertThat(older.getStatus()).isEqualTo(EnrollmentStatus.WAITLISTED);
    }

    @Test
    void skippedCourseStudentKeepsItsPlaceAndTheNextEligibleOneIsPromoted() {
        Course course = course(2054);
        CourseEnrollment skipped = courseEnrollment(course, EnrollmentStatus.WAITLISTED, BASE_TIME, null);
        CourseEnrollment eligible = courseEnrollment(course, EnrollmentStatus.WAITLISTED, BASE_TIME.plusDays(1), null);
        courseEnrollment(course, EnrollmentStatus.ENROLLED, BASE_TIME, null);
        entityManager.flush();
        when(eligibility.evaluate(any())).thenAnswer(invocation -> {
            WaitlistCandidate candidate = invocation.getArgument(0);
            return candidate.enrollmentId().equals(skipped.getId())
                    ? WaitlistVerdict.skippedBecause(WaitlistVerdictReason.ADMISSION_RECEIPT_PENDING)
                    : WaitlistVerdict.passing();
        });

        waitlistPromoter.promoteNext(WaitlistKind.COURSE, course.getId());

        assertThat(eligible.getStatus()).isEqualTo(EnrollmentStatus.ENROLLED);
        assertThat(skipped.getStatus()).isEqualTo(EnrollmentStatus.WAITLISTED);
    }

    @Test
    void prioritizedModuleStudentIsPromotedBeforeTheOldestOne() {
        CourseSubjectSection section = moduleSection(2055);
        CourseEnrollment parent = courseEnrollment(section.getCourseSubject().getCourse(), EnrollmentStatus.ENROLLED, BASE_TIME, null);
        CourseSubjectSectionEnrollment older = moduleEnrollment(section, parent, EnrollmentStatus.WAITLISTED, BASE_TIME, null);
        CourseSubjectSectionEnrollment prioritized = moduleEnrollment(section, parent, EnrollmentStatus.WAITLISTED, BASE_TIME.plusDays(1), BASE_TIME.plusDays(2));
        moduleEnrollment(section, parent, EnrollmentStatus.ENROLLED, BASE_TIME, null);
        entityManager.flush();

        waitlistPromoter.promoteNext(WaitlistKind.COURSE_SECTION, section.getId());

        assertThat(prioritized.getStatus()).isEqualTo(EnrollmentStatus.ENROLLED);
        assertThat(older.getStatus()).isEqualTo(EnrollmentStatus.WAITLISTED);
    }

    @Test
    void skippedModuleStudentKeepsItsPlaceAndTheNextEligibleOneIsPromoted() {
        CourseSubjectSection section = moduleSection(2056);
        CourseEnrollment parent = courseEnrollment(section.getCourseSubject().getCourse(), EnrollmentStatus.ENROLLED, BASE_TIME, null);
        CourseSubjectSectionEnrollment skipped = moduleEnrollment(section, parent, EnrollmentStatus.WAITLISTED, BASE_TIME, null);
        CourseSubjectSectionEnrollment eligible = moduleEnrollment(section, parent, EnrollmentStatus.WAITLISTED, BASE_TIME.plusDays(1), null);
        moduleEnrollment(section, parent, EnrollmentStatus.ENROLLED, BASE_TIME, null);
        entityManager.flush();
        when(eligibility.evaluate(any())).thenAnswer(invocation -> {
            WaitlistCandidate candidate = invocation.getArgument(0);
            return candidate.enrollmentId().equals(skipped.getId())
                    ? WaitlistVerdict.skippedBecause(WaitlistVerdictReason.ADMISSION_RECEIPT_PENDING)
                    : WaitlistVerdict.passing();
        });

        waitlistPromoter.promoteNext(WaitlistKind.COURSE_SECTION, section.getId());

        assertThat(eligible.getStatus()).isEqualTo(EnrollmentStatus.ENROLLED);
        assertThat(skipped.getStatus()).isEqualTo(EnrollmentStatus.WAITLISTED);
    }

    private Course course(int year) {
        Term term = new CycleTestFixtures(entityManager).term(year);
        Course course = new Course();
        course.setName("Curso " + year);
        course.setTerm(term);
        course.setCapacity(2);
        course.setIsActive(StatusType.ACTIVE);
        course.setTeachingMode(TeachingMode.ONLINE);
        entityManager.persist(course);
        return course;
    }

    private CourseSubjectSection moduleSection(int year) {
        Course course = course(year);
        CourseSubject module = new CourseSubject();
        module.setCourse(course);
        module.setName("Módulo " + year);
        module.setStatus(StatusType.ACTIVE);
        entityManager.persist(module);
        CourseSubjectSection section = new CourseSubjectSection();
        section.setName("Comisión " + year);
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

    private CourseSubjectSectionEnrollment moduleEnrollment(CourseSubjectSection section, CourseEnrollment parent,
                                                            EnrollmentStatus status, LocalDateTime joinedAt, LocalDateTime prioritizedAt) {
        CourseSubjectSectionEnrollment enrollment = new CourseSubjectSectionEnrollment();
        enrollment.setStudent(student());
        enrollment.setCourseEnrollment(parent);
        enrollment.setCourseSubjectSection(section);
        enrollment.setStatus(status);
        enrollment.setEnrolledAt(joinedAt);
        enrollment.setAttemptNumber(1);
        enrollment.setWaitlistPrioritizedAt(prioritizedAt);
        entityManager.persist(enrollment);
        return enrollment;
    }
}
