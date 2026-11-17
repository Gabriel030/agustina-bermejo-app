package com.red_escuela.backend.enrollment.service;

import com.red_escuela.backend.academicstructure.CycleTestFixtures;
import com.red_escuela.backend.academicstructure.entity.AcademicOfferingCycle;
import com.red_escuela.backend.academicstructure.entity.AcademicOfferingCycleStatus;
import com.red_escuela.backend.academicstructure.entity.Course;
import com.red_escuela.backend.academicstructure.entity.Program;
import com.red_escuela.backend.academicstructure.entity.Subject;
import com.red_escuela.backend.academicstructure.entity.SubjectSection;
import com.red_escuela.backend.academicstructure.entity.TeachingMode;
import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.config.ConcurrentRace;
import com.red_escuela.backend.enrollment.dto.WaitlistKind;
import com.red_escuela.backend.enrollment.entity.CourseEnrollment;
import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentStatus;
import com.red_escuela.backend.enrollment.entity.SubjectSectionEnrollment;
import com.red_escuela.backend.enrollment.api.WithdrawalCause;
import com.red_escuela.backend.enrollment.repository.CourseEnrollmentRepository;
import com.red_escuela.backend.enrollment.repository.WaitlistEntryRow;
import com.red_escuela.backend.identity.entity.Institution;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.identity.entity.StudentOnboardingStatus;
import com.red_escuela.backend.shared.domain.StatusType;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.Callable;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class WaitlistPromotionIT extends AbstractIntegrationTest {

    private static final LocalDateTime BASE_TIME = LocalDateTime.of(2047, 2, 3, 9, 0);

    @Autowired
    private EntityManager entityManager;
    @Autowired
    private PlatformTransactionManager transactionManager;
    @Autowired
    private WaitlistPromoter waitlistPromoter;
    @Autowired
    private CourseEnrollmentService courseEnrollmentService;
    @Autowired
    private CourseEnrollmentRepository courseEnrollmentRepository;

    private record ProgramQueue(Long sectionId, List<Long> waitingIds) {
    }

    private record CourseQueue(Long courseId, Long seatedId, Long waitingId) {
    }

    @Test
    void concurrentPromotionsNeverExceedCapacity() throws Exception {
        ProgramQueue queue = inTransaction(() -> programQueue(2047, 1, 3, false));
        Callable<Boolean> promotion = () -> {
            waitlistPromoter.promoteNext(WaitlistKind.PROGRAM_SECTION, queue.sectionId());
            return true;
        };

        ConcurrentRace.run(List.of(promotion, promotion, promotion));

        assertThat(countProgramEnrollments(queue.sectionId(), EnrollmentStatus.ENROLLED)).isEqualTo(2);
        assertThat(countProgramEnrollments(queue.sectionId(), EnrollmentStatus.WAITLISTED)).isEqualTo(2);
    }

    @Test
    void prioritizedEntryIsPromotedBeforeOlderEntries() {
        ProgramQueue queue = inTransaction(() -> programQueue(2048, 1, 3, true));

        waitlistPromoter.promoteNext(WaitlistKind.PROGRAM_SECTION, queue.sectionId());

        assertThat(statusOf(queue.waitingIds().get(2))).isEqualTo(EnrollmentStatus.ENROLLED);
        assertThat(statusOf(queue.waitingIds().get(0))).isEqualTo(EnrollmentStatus.WAITLISTED);
        assertThat(statusOf(queue.waitingIds().get(1))).isEqualTo(EnrollmentStatus.WAITLISTED);
    }

    @Test
    void oldestEntryIsPromotedFirstWhenNobodyIsPrioritized() {
        ProgramQueue queue = inTransaction(() -> programQueue(2049, 1, 3, false));

        waitlistPromoter.promoteNext(WaitlistKind.PROGRAM_SECTION, queue.sectionId());

        assertThat(statusOf(queue.waitingIds().get(0))).isEqualTo(EnrollmentStatus.ENROLLED);
        assertThat(statusOf(queue.waitingIds().get(1))).isEqualTo(EnrollmentStatus.WAITLISTED);
    }

    @Test
    void nonpaymentWithdrawalDoesNotPromoteCourseStudent() {
        CourseQueue queue = inTransaction(() -> courseQueue(2050));

        inTransaction(() -> {
            courseEnrollmentService.withdrawForNonpayment(queue.seatedId(), LocalDate.now());
            return true;
        });

        assertThat(inTransaction(() -> entityManager.find(CourseEnrollment.class, queue.waitingId()).getStatus()))
                .isEqualTo(EnrollmentStatus.WAITLISTED);
    }

    @Test
    void voluntaryCourseWithdrawalDoesNotPromoteAWaitlistedStudentWithoutApprovedReceipt() {
        CourseQueue queue = inTransaction(() -> courseQueue(2026));

        Long actorId = inTransaction(() -> new CycleTestFixtures(entityManager).person().getId());
        inTransaction(() -> {
            courseEnrollmentService.withdraw(queue.seatedId(), new EnrollmentWithdrawal(null, actorId, WithdrawalCause.VOLUNTARY));
            return true;
        });

        assertThat(inTransaction(() -> entityManager.find(CourseEnrollment.class, queue.waitingId()).getStatus()))
                .isEqualTo(EnrollmentStatus.WAITLISTED);
    }

    @Test
    void courseWaitlistIsOrderedWithPrioritizedFirstThenOldest() {
        CourseQueue queue = inTransaction(() -> courseQueue(2052));
        Long prioritizedId = inTransaction(() -> {
            CourseEnrollment late = courseEnrollment(new CycleTestFixtures(entityManager), institution(),
                    entityManager.find(Course.class, queue.courseId()), EnrollmentStatus.WAITLISTED);
            late.setEnrollmentDate(BASE_TIME.plusDays(2));
            late.setWaitlistPrioritizedAt(BASE_TIME.plusDays(3));
            entityManager.flush();
            return late.getId();
        });

        List<Long> order = inTransaction(() -> courseEnrollmentRepository
                .findWaitlistEntryRows(queue.courseId(), EnrollmentStatus.WAITLISTED).stream()
                .map(WaitlistEntryRow::getEnrollmentId).toList());

        assertThat(order).containsExactly(prioritizedId, queue.waitingId());
    }

    private <T> T inTransaction(Callable<T> work) {
        return new TransactionTemplate(transactionManager).execute(status -> {
            try {
                return work.call();
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });
    }

    private EnrollmentStatus statusOf(Long enrollmentId) {
        return inTransaction(() -> entityManager.find(SubjectSectionEnrollment.class, enrollmentId).getStatus());
    }

    private long countProgramEnrollments(Long sectionId, EnrollmentStatus status) {
        return inTransaction(() -> entityManager.createQuery(
                        "select count(e) from SubjectSectionEnrollment e where e.subjectSection.id = :id and e.status = :status", Long.class)
                .setParameter("id", sectionId).setParameter("status", status).getSingleResult());
    }

    private ProgramQueue programQueue(int year, int freeSeats, int waiting, boolean prioritizeLast) {
        CycleTestFixtures fixtures = new CycleTestFixtures(entityManager);
        Program program = fixtures.program();
        Term term = fixtures.term(year);
        Subject subject = fixtures.subject();
        AcademicOfferingCycle cycle = fixtures.programCycle(program, term, AcademicOfferingCycleStatus.APPROVED);
        SubjectSection section = fixtures.section(subject, term, StatusType.ACTIVE, cycle);
        section.setCapacity(1 + freeSeats);
        Institution institution = institution();
        programEnrollment(fixtures, institution, program, section, EnrollmentStatus.ENROLLED, BASE_TIME);
        List<Long> waitingIds = new java.util.ArrayList<>();
        for (int index = 0; index < waiting; index++) {
            SubjectSectionEnrollment entry = programEnrollment(fixtures, institution, program, section,
                    EnrollmentStatus.WAITLISTED, BASE_TIME.plusHours(index));
            if (prioritizeLast && index == waiting - 1) {
                entry.setWaitlistPrioritizedAt(BASE_TIME.plusDays(1));
            }
            waitingIds.add(entry.getId());
        }
        entityManager.flush();
        return new ProgramQueue(section.getId(), waitingIds);
    }

    private CourseQueue courseQueue(int year) {
        CycleTestFixtures fixtures = new CycleTestFixtures(entityManager);
        Term term = fixtures.term(year);
        Course course = new Course();
        course.setName("Curso " + year);
        course.setTerm(term);
        course.setCapacity(1);
        course.setIsActive(StatusType.ACTIVE);
        course.setTeachingMode(TeachingMode.ONLINE);
        entityManager.persist(course);
        Institution institution = institution();
        CourseEnrollment seated = courseEnrollment(fixtures, institution, course, EnrollmentStatus.ENROLLED);
        CourseEnrollment waiting = courseEnrollment(fixtures, institution, course, EnrollmentStatus.WAITLISTED);
        entityManager.flush();
        return new CourseQueue(course.getId(), seated.getId(), waiting.getId());
    }

    private Institution institution() {
        Institution institution = new Institution();
        institution.setName("Instituto");
        entityManager.persist(institution);
        return institution;
    }

    private Student student(CycleTestFixtures fixtures, Institution institution) {
        Student student = new Student();
        student.setPerson(fixtures.person());
        student.setInstitution(institution);
        student.setOnboardingStatus(StudentOnboardingStatus.CONFIRMADO);
        entityManager.persist(student);
        return student;
    }

    private CourseEnrollment courseEnrollment(CycleTestFixtures fixtures, Institution institution, Course course, EnrollmentStatus status) {
        CourseEnrollment enrollment = new CourseEnrollment();
        enrollment.setStudent(student(fixtures, institution));
        enrollment.setCourse(course);
        enrollment.setStatus(status);
        enrollment.setEnrollmentDate(course.getTerm().getStartDate().plusDays(5));
        entityManager.persist(enrollment);
        return enrollment;
    }

    private SubjectSectionEnrollment programEnrollment(CycleTestFixtures fixtures, Institution institution, Program program,
                                                       SubjectSection section, EnrollmentStatus status, LocalDateTime enrolledAt) {
        Student student = student(fixtures, institution);
        ProgramEnrollment programEnrollment = new ProgramEnrollment();
        programEnrollment.setStudent(student);
        programEnrollment.setProgram(program);
        programEnrollment.setStatus(ProgramEnrollmentStatus.ENROLLED);
        programEnrollment.setEnrollmentDate(LocalDateTime.now());
        entityManager.persist(programEnrollment);
        SubjectSectionEnrollment enrollment = new SubjectSectionEnrollment();
        enrollment.setProgramEnrollment(programEnrollment);
        enrollment.setStudent(student);
        enrollment.setSubjectSection(section);
        enrollment.setStatus(status);
        enrollment.setEnrolledAt(enrolledAt);
        enrollment.setAttemptNumber(1);
        entityManager.persist(enrollment);
        return enrollment;
    }
}
