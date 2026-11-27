package com.red_escuela.backend.enrollment;

import static com.red_escuela.backend.billing.support.TransferReceiptFixtures.newIdempotencyKey;
import static com.red_escuela.backend.billing.support.TransferReceiptFixtures.signedInAs;
import static com.red_escuela.backend.billing.support.TransferReceiptFixtures.transferReceiptOf;
import static com.red_escuela.backend.shared.domain.TestMoney.ars;
import static org.assertj.core.api.Assertions.assertThat;

import com.red_escuela.backend.academicstructure.entity.Course;
import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.billing.invoice.domain.FeeType;
import com.red_escuela.backend.billing.support.PriceFixtures;
import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.enrollment.dto.CreateCourseEnrollmentRequest;
import com.red_escuela.backend.enrollment.entity.CourseEnrollment;
import com.red_escuela.backend.enrollment.entity.CourseEnrollmentApplication;
import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.enrollment.repository.CourseEnrollmentRepository;
import com.red_escuela.backend.enrollment.service.CourseEnrollmentApplicationService;
import com.red_escuela.backend.enrollment.service.CourseWaitlistExpiration;
import com.red_escuela.backend.identity.entity.Institution;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.identity.entity.StudentOnboardingStatus;
import com.red_escuela.backend.platform.infrastructure.FeatureSnapshotHolder;
import com.red_escuela.backend.shared.domain.Address;
import com.red_escuela.backend.shared.domain.StatusType;
import com.red_escuela.backend.testsupport.TermFixtures;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@ActiveProfiles("test")
class EnrollmentWithBillingOffIT extends AbstractIntegrationTest {

    @Autowired private CourseEnrollmentApplicationService applicationService;
    @Autowired private CourseWaitlistExpiration courseWaitlistExpiration;
    @Autowired private CourseEnrollmentRepository courseEnrollmentRepository;
    @Autowired private TransactionTemplate transactions;
    @Autowired private EntityManager entityManager;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private FeatureSnapshotHolder snapshotHolder;

    @AfterEach
    void restoreBillingFull() {
        SecurityContextHolder.clearContext();
        jdbc.update("update platform_feature set enabled = true, mode = 'FULL', version = 0 where code = 'BILLING'");
        snapshotHolder.refresh();
    }

    @Test
    void courseEnrollmentSucceedsWithoutFeeWhenBillingIsOff() {
        switchBillingTo("OFF", false);
        Student student = persistStudent();
        Course course = persistPricedCourse(20);

        CourseEnrollmentApplication application = applicationService.requestWithPayment(signedInAs(student),
                new CreateCourseEnrollmentRequest(course.getId(), null), null, newIdempotencyKey());

        assertThat(application.getCourseEnrollment()).isNotNull();
        assertThat(application.getInvoiceId()).isNull();
        assertThat(courseEnrollmentRepository.findByStudentId(student.getId())).hasSize(1);
        assertThat(billingRowsOf(student)).isZero();
    }

    @Test
    void courseWaitlistExpiryRunsWithoutRefundWhenBillingIsOff() {
        switchBillingTo("OFF", false);
        Student seated = persistStudent();
        Student waitlisted = persistStudent();
        Course course = persistPricedCourse(1);
        applicationService.requestWithPayment(signedInAs(seated), new CreateCourseEnrollmentRequest(course.getId(), null), null, newIdempotencyKey());
        CourseEnrollment waiting = applicationService.enrollByStaff(waitlisted.getId(), new CreateCourseEnrollmentRequest(course.getId(), null));
        assertThat(waiting.getStatus()).isEqualTo(EnrollmentStatus.WAITLISTED);

        courseWaitlistExpiration.expire(course.getId());

        assertThat(courseEnrollmentRepository.findById(waiting.getId()).orElseThrow().getStatus()).isEqualTo(EnrollmentStatus.WAITLIST_EXPIRED);
        assertThat(billingRowsOf(waitlisted)).isZero();
        assertThat(billingRowsOf(seated)).isZero();
    }

    @Test
    void enrollmentFeeIsRequiredAgainWhenBillingIsFull() {
        Student student = persistStudent();
        Course course = persistPricedCourse(20);

        CourseEnrollmentApplication application = applicationService.requestWithPayment(signedInAs(student),
                new CreateCourseEnrollmentRequest(course.getId(), null), transferReceiptOf(15000), newIdempotencyKey());

        assertThat(application.getInvoiceId()).isNotNull();
        assertThat(application.getCourseEnrollment()).isNull();
        assertThat(billingRowsOf(student)).isPositive();
    }

    private int billingRowsOf(Student student) {
        return jdbc.queryForObject("select (select count(*) from invoice where student_current_account_id in (select id from student_current_account where student_id = ?))"
                + " + (select count(*) from student_current_account where student_id = ?)", Integer.class, student.getId(), student.getId());
    }

    private void switchBillingTo(String mode, boolean enabled) {
        jdbc.update("update platform_feature set enabled = ?, mode = ? where code = 'BILLING'", enabled, mode);
        snapshotHolder.refresh();
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
        return transactions.execute(status -> {
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

    private Course persistPricedCourse(int capacity) {
        return transactions.execute(status -> {
            Term term = TermFixtures.newTerm();
            term.setYear(2026);
            term.setStatus(StatusType.ACTIVE);
            entityManager.persist(term);
            Course course = new Course();
            course.setVisibleInCatalog(true);
            course.setName("Curso " + System.nanoTime());
            course.setTerm(term);
            course.setCapacity(capacity);
            course.setIsActive(StatusType.ACTIVE);
            entityManager.persist(course);
            PriceFixtures.persistPriceVersion(null, course.getId(), FeeType.MONTHLY_INSTALLMENT, "Precio de prueba",
                    ars(BigDecimal.valueOf(15000)), LocalDate.now().minusDays(1), persistPerson("DIR" + System.nanoTime()).getId());
            return course;
        });
    }
}
