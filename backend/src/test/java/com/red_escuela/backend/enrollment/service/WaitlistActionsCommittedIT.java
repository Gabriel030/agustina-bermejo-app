package com.red_escuela.backend.enrollment.service;

import com.red_escuela.backend.academicstructure.entity.Course;
import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.billing.invoice.domain.FeeType;
import com.red_escuela.backend.billing.support.PriceFixtures;
import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.enrollment.dto.CreateCourseEnrollmentRequest;
import com.red_escuela.backend.enrollment.dto.WaitlistKind;
import com.red_escuela.backend.enrollment.entity.CourseEnrollment;
import com.red_escuela.backend.enrollment.entity.CourseEnrollmentApplication;
import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.enrollment.exception.WaitlistCandidateNotEligibleException;
import com.red_escuela.backend.enrollment.exception.WaitlistEntryNotWaitingException;
import com.red_escuela.backend.enrollment.exception.WaitlistNoFreeSeatException;
import com.red_escuela.backend.enrollment.exception.WaitlistRemovalBlockedByPendingReceiptException;
import com.red_escuela.backend.identity.entity.Institution;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.identity.entity.StudentOnboardingStatus;
import com.red_escuela.backend.shared.domain.Address;
import com.red_escuela.backend.shared.domain.StatusType;
import com.red_escuela.backend.shared.time.MutableClock;
import com.red_escuela.backend.testsupport.TermFixtures;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static com.red_escuela.backend.billing.support.TransferReceiptFixtures.newIdempotencyKey;
import static com.red_escuela.backend.billing.support.TransferReceiptFixtures.signedInAs;
import static com.red_escuela.backend.billing.support.TransferReceiptFixtures.transferReceiptOf;
import static com.red_escuela.backend.shared.domain.TestMoney.ars;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class WaitlistActionsCommittedIT extends AbstractIntegrationTest {

    private static final LocalDate UPLOAD_DAY = LocalDate.of(2027, 3, 9);
    private static final String REASON = "Motivo suficiente";

    @Autowired
    private WaitlistEntryActions actions;
    @Autowired
    private CourseEnrollmentApplicationService applicationService;
    @Autowired
    private TransactionTemplate transactionTemplate;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void removalIsBlockedWhileTheReceiptIsPendingReview() {
        Student student = persistStudent();
        Course course = persistPricedCourse(20);
        CourseEnrollmentApplication application = requestWithReceipt(student, course);
        CourseEnrollment entry = waitlistedWith(course, student, application);

        assertThatThrownBy(() -> removeAs(student, entry)).isInstanceOf(WaitlistRemovalBlockedByPendingReceiptException.class);

        assertThat(statusOf(entry)).isEqualTo("WAITLISTED");
        assertThat(auditCount("WAITLIST_REMOVED_BY_STAFF", entry.getId())).isZero();
    }

    @Test
    void removalIsBlockedWhenTheReceiptWasAlreadyApproved() {
        Student student = persistStudent();
        Course course = persistPricedCourse(20);
        CourseEnrollmentApplication application = requestWithReceipt(student, course);
        CourseEnrollment entry = waitlistedWith(course, student, application);
        setReceiptStatus(application, "APPROVED");

        assertThatThrownBy(() -> removeAs(student, entry)).isInstanceOf(WaitlistRemovalBlockedByPendingReceiptException.class);

        assertThat(statusOf(entry)).isEqualTo("WAITLISTED");
    }

    @Test
    void removalIsAllowedWhenEveryReceiptWasRejected() {
        Student student = persistStudent();
        Course course = persistPricedCourse(20);
        CourseEnrollmentApplication application = requestWithReceipt(student, course);
        CourseEnrollment entry = waitlistedWith(course, student, application);
        setReceiptStatus(application, "REJECTED");

        removeAs(student, entry);

        assertThat(statusOf(entry)).isEqualTo("WITHDRAWN");
        assertThat(auditCount("WAITLIST_REMOVED_BY_STAFF", entry.getId())).isEqualTo(1);
    }

    @Test
    void promotingAnEntryWithAnApprovedReceiptEnrollsItOnceAndTheSecondPromoteFails() {
        Student student = persistStudent();
        Course course = persistPricedCourse(5);
        CourseEnrollmentApplication application = requestWithReceipt(student, course);
        CourseEnrollment entry = waitlistedWith(course, student, application);
        setReceiptStatus(application, "APPROVED");

        promoteAs(student, entry);

        assertThat(statusOf(entry)).isEqualTo("ENROLLED");
        assertThat(auditCount("WAITLIST_PROMOTED_BY_STAFF", entry.getId())).isEqualTo(1);
        assertThatThrownBy(() -> promoteAs(student, entry)).isInstanceOf(WaitlistEntryNotWaitingException.class);
    }

    @Test
    void promotingAnEntryWithoutAnApprovedReceiptFailsAsNotEligible() {
        Student student = persistStudent();
        Course course = persistPricedCourse(5);
        CourseEnrollmentApplication application = requestWithReceipt(student, course);
        CourseEnrollment entry = waitlistedWith(course, student, application);

        assertThatThrownBy(() -> promoteAs(student, entry)).isInstanceOf(WaitlistCandidateNotEligibleException.class);

        assertThat(statusOf(entry)).isEqualTo("WAITLISTED");
        assertThat(auditCount("WAITLIST_PROMOTED_BY_STAFF", entry.getId())).isZero();
    }

    @Test
    void concurrentPromotionsNeverExceedCapacity() throws Exception {
        Course course = persistPricedCourse(1);
        Student first = persistStudent();
        Student second = persistStudent();
        CourseEnrollment firstEntry = approvedWaitlisted(course, first);
        CourseEnrollment secondEntry = approvedWaitlisted(course, second);

        List<Throwable> outcomes = runConcurrently(
                () -> promoteAs(first, firstEntry),
                () -> promoteAs(second, secondEntry));

        assertThat(outcomes).filteredOn(outcome -> outcome == null).hasSize(1);
        assertThat(outcomes).filteredOn(outcome -> outcome != null).singleElement().isInstanceOf(WaitlistNoFreeSeatException.class);
        assertThat(enrolledCount(course)).isEqualTo(1);
    }

    @Test
    void doubleClickPromoteOnTheSameEntryEnrollsItOnceAndAuditsOnce() throws Exception {
        Course course = persistPricedCourse(5);
        Student student = persistStudent();
        CourseEnrollment entry = approvedWaitlisted(course, student);

        List<Throwable> outcomes = runConcurrently(() -> promoteAs(student, entry), () -> promoteAs(student, entry));

        assertThat(outcomes).filteredOn(outcome -> outcome == null).hasSize(1);
        assertThat(outcomes).filteredOn(outcome -> outcome != null).singleElement().isInstanceOf(WaitlistEntryNotWaitingException.class);
        assertThat(auditCount("WAITLIST_PROMOTED_BY_STAFF", entry.getId())).isEqualTo(1);
        assertThat(enrolledCount(course)).isEqualTo(1);
    }

    @Test
    void doubleClickPrioritizeOnTheSameEntryAuditsOnce() throws Exception {
        Course course = persistPricedCourse(1);
        Student student = persistStudent();
        persistWaitlistedEnrollment(course, persistStudent());
        CourseEnrollment entry = persistWaitlistedEnrollment(course, student);

        List<Throwable> outcomes = runConcurrently(() -> prioritizeAs(student, entry), () -> prioritizeAs(student, entry));

        assertThat(outcomes).containsOnlyNulls();
        assertThat(auditCount("WAITLIST_PRIORITIZED_BY_STAFF", entry.getId())).isEqualTo(1);
    }

    private List<Throwable> runConcurrently(Runnable first, Runnable second) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);
        try {
            List<Future<Throwable>> futures = new ArrayList<>();
            for (Runnable action : List.of(first, second)) {
                Callable<Throwable> task = () -> {
                    ready.countDown();
                    go.await();
                    try {
                        action.run();
                        return null;
                    } catch (RuntimeException failure) {
                        return failure;
                    }
                };
                futures.add(executor.submit(task));
            }
            ready.await();
            go.countDown();
            List<Throwable> outcomes = new ArrayList<>();
            for (Future<Throwable> future : futures) {
                outcomes.add(future.get());
            }
            return outcomes;
        } finally {
            executor.shutdownNow();
        }
    }

    private void promoteAs(Student student, CourseEnrollment entry) {
        signedInAs(student);
        actions.promote(WaitlistKind.COURSE, entry.getId());
    }

    private void prioritizeAs(Student student, CourseEnrollment entry) {
        signedInAs(student);
        actions.prioritize(WaitlistKind.COURSE, entry.getId(), REASON);
    }

    private void removeAs(Student student, CourseEnrollment entry) {
        signedInAs(student);
        actions.remove(WaitlistKind.COURSE, entry.getId(), REASON);
    }

    private String statusOf(CourseEnrollment entry) {
        return jdbc.queryForObject("select status from course_enrollment where id = ?", String.class, entry.getId());
    }

    private long enrolledCount(Course course) {
        return jdbc.queryForObject("select count(*) from course_enrollment where course_id = ? and status = 'ENROLLED'", Long.class, course.getId());
    }

    private long auditCount(String action, Long entityId) {
        return jdbc.queryForObject("select count(*) from audit_log where action = ? and entity_id = ?", Long.class, action, entityId);
    }

    private CourseEnrollmentApplication requestWithReceipt(Student student, Course course) {
        MutableClock.INSTANCE.setDate(UPLOAD_DAY);
        return applicationService.requestWithPayment(signedInAs(student), new CreateCourseEnrollmentRequest(course.getId(), null),
                transferReceiptOf(15000), newIdempotencyKey());
    }

    private CourseEnrollment waitlistedWith(Course course, Student student, CourseEnrollmentApplication application) {
        CourseEnrollment entry = persistWaitlistedEnrollment(course, student);
        linkApplication(entry, application);
        return entry;
    }

    private CourseEnrollment approvedWaitlisted(Course course, Student student) {
        CourseEnrollmentApplication application = requestWithReceipt(student, course);
        CourseEnrollment entry = waitlistedWith(course, student, application);
        setReceiptStatus(application, "APPROVED");
        return entry;
    }

    private void setReceiptStatus(CourseEnrollmentApplication application, String status) {
        jdbc.update("update bank_transfer_receipt set status = ? where id = ?", status, application.getPaymentReceipt().getId());
    }

    private void linkApplication(CourseEnrollment entry, CourseEnrollmentApplication application) {
        jdbc.update("update course_enrollment_application set course_enrollment_id = ? where id = ?", entry.getId(), application.getId());
    }

    private CourseEnrollment persistWaitlistedEnrollment(Course course, Student student) {
        return transactionTemplate.execute(status -> {
            CourseEnrollment enrollment = new CourseEnrollment();
            enrollment.setStudent(student);
            enrollment.setCourse(course);
            enrollment.setStatus(EnrollmentStatus.WAITLISTED);
            enrollment.setEnrollmentDate(LocalDateTime.now());
            entityManager.persist(enrollment);
            return enrollment;
        });
    }

    private Person persistPerson(String dni) {
        Address address = new Address();
        entityManager.persist(address);
        Person person = new Person();
        person.setName("Persona");
        person.setLastname(dni);
        person.setDni(dni);
        person.setUsername(dni);
        person.setEmail(dni + "@example.com");
        person.setAddress(address);
        person.setStatus(StatusType.ACTIVE);
        entityManager.persist(person);
        return person;
    }

    private Student persistStudent() {
        return transactionTemplate.execute(status -> {
            Institution institution = new Institution();
            institution.setName("Instituto " + System.nanoTime());
            entityManager.persist(institution);
            Student student = new Student();
            student.setPerson(persistPerson("7" + System.nanoTime()));
            student.setInstitution(institution);
            student.setOnboardingStatus(StudentOnboardingStatus.CONFIRMADO);
            entityManager.persist(student);
            return student;
        });
    }

    private Course persistCourse(int capacity) {
        return transactionTemplate.execute(status -> {
            Term term = TermFixtures.newTerm();
            term.setYear(2027);
            term.setStatus(StatusType.ACTIVE);
            entityManager.persist(term);
            Course course = new Course();
            course.setVisibleInCatalog(true);
            course.setName("Curso " + System.nanoTime());
            course.setTerm(term);
            course.setCapacity(capacity);
            course.setIsActive(StatusType.ACTIVE);
            entityManager.persist(course);
            return course;
        });
    }

    private Course persistPricedCourse(int capacity) {
        Course course = persistCourse(capacity);
        transactionTemplate.executeWithoutResult(status -> PriceFixtures.persistPriceVersion(null, course.getId(), FeeType.MONTHLY_INSTALLMENT,
                "Precio de prueba", ars(BigDecimal.valueOf(15000)), UPLOAD_DAY.minusDays(30), persistPerson("DIR" + System.nanoTime()).getId()));
        return course;
    }
}
