package com.red_escuela.backend.enrollment.service;

import com.red_escuela.backend.enrollment.dto.CourseAdmissionPreviewResponse;
import com.red_escuela.backend.academicstructure.entity.Course;
import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.billing.invoice.domain.FeeType;
import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.billing.invoice.repository.InvoiceRepository;
import com.red_escuela.backend.billing.support.PriceFixtures;
import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.enrollment.dto.CreateCourseEnrollmentRequest;
import com.red_escuela.backend.enrollment.entity.CourseEnrollmentApplication;
import com.red_escuela.backend.identity.entity.Institution;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.identity.entity.StudentOnboardingStatus;
import com.red_escuela.backend.shared.domain.Address;
import com.red_escuela.backend.shared.domain.Money;
import com.red_escuela.backend.shared.domain.StatusType;
import com.red_escuela.backend.testsupport.TermFixtures;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static com.red_escuela.backend.billing.support.TransferReceiptFixtures.newIdempotencyKey;
import static com.red_escuela.backend.billing.support.TransferReceiptFixtures.signedInAs;
import static com.red_escuela.backend.billing.support.TransferReceiptFixtures.transferReceiptOf;
import static com.red_escuela.backend.shared.domain.TestMoney.ars;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class CourseAdmissionPreviewIT extends AbstractIntegrationTest {
    private static final List<String> TABLES_THAT_MUST_NOT_GROW = List.of("invoice", "invoice_line", "student_current_account", "course_enrollment_application",
            "course_enrollment", "student_discount");

    @Autowired private CourseAdmissionPreviewService previewService;
    @Autowired private CourseEnrollmentApplicationService applicationService;
    @Autowired private InvoiceRepository invoiceRepository;
    @Autowired private TransactionTemplate transactionTemplate;
    @Autowired private EntityManager entityManager;
    @Autowired private JdbcTemplate jdbcTemplate;

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

    private Course persistCourseWithMarketingDiscount(String discountType, BigDecimal discountValue) {
        return transactionTemplate.execute(status -> {
            Term term = TermFixtures.newTerm();
            term.setYear(2026);
            term.setStatus(StatusType.ACTIVE);
            entityManager.persist(term);
            Course course = new Course();
            course.setVisibleInCatalog(true);
            course.setName("Curso " + System.nanoTime());
            course.setTerm(term);
            course.setCapacity(20);
            course.setIsActive(StatusType.ACTIVE);
            entityManager.persist(course);
            PriceFixtures.persistPriceVersion(null, course.getId(), FeeType.MONTHLY_INSTALLMENT, "Precio de prueba", ars(BigDecimal.valueOf(15000)),
                    LocalDate.now().minusDays(1), persistPerson("DIR" + System.nanoTime()).getId(), discountType, discountValue);
            return course;
        });
    }

    private Map<String, Long> rowCounts() {
        Map<String, Long> counts = new java.util.LinkedHashMap<>();
        TABLES_THAT_MUST_NOT_GROW.forEach(table -> counts.put(table, jdbcTemplate.queryForObject("select count(*) from " + table, Long.class)));
        counts.put("max_invoice_id", jdbcTemplate.queryForObject("select coalesce(max(id), 0) from invoice", Long.class));
        return counts;
    }

    private Money totalOfIssuedAdmissionInvoice(CourseEnrollmentApplication application) {
        Invoice invoice = invoiceRepository.findInvoiceWithLinesById(application.getInvoiceId()).orElseThrow();
        return invoice.getAmounts().total().value();
    }

    @Test
    @Tag("D-L1-07")
    void previewOfAPaidCourseReportsTheRealAdmissionTotalAndPersistsNothing() {
        Student student = persistStudent();
        Course course = persistCourseWithMarketingDiscount("PERCENTAGE", BigDecimal.valueOf(20));
        Map<String, Long> before = rowCounts();

        CourseAdmissionPreviewResponse preview = previewService.previewAdmissionCharge(student.getId(), course.getId());

        assertThat(rowCounts()).isEqualTo(before);
        assertThat(preview.requiresPayment()).isTrue();
        CourseEnrollmentApplication application = applicationService.requestWithPayment(signedInAs(student), new CreateCourseEnrollmentRequest(course.getId(), null),
                transferReceiptOf(12000), newIdempotencyKey());
        assertThat(preview.amount()).isEqualTo(totalOfIssuedAdmissionInvoice(application));
        assertThat(preview.amount()).isEqualTo(ars(12000));
    }

    @Test
    @Tag("D-L1-07")
    void repeatedPreviewsNeverCollideWithTheRealEmission() {
        Student student = persistStudent();
        Course course = persistCourseWithMarketingDiscount(null, null);

        previewService.previewAdmissionCharge(student.getId(), course.getId());
        previewService.previewAdmissionCharge(student.getId(), course.getId());
        CourseEnrollmentApplication application = applicationService.requestWithPayment(signedInAs(student), new CreateCourseEnrollmentRequest(course.getId(), null),
                transferReceiptOf(15000), newIdempotencyKey());

        assertThat(totalOfIssuedAdmissionInvoice(application)).isEqualTo(ars(15000));
    }

    @Test
    @Tag("D-L1-07")
    void previewAfterTheAdmissionWasAlreadyIssuedFailsLikeTheRealEmissionWould() {
        Student student = persistStudent();
        Course course = persistCourseWithMarketingDiscount(null, null);
        applicationService.requestWithPayment(signedInAs(student), new CreateCourseEnrollmentRequest(course.getId(), null), transferReceiptOf(15000), newIdempotencyKey());

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> previewService.previewAdmissionCharge(student.getId(), course.getId()))
                .isInstanceOf(com.red_escuela.backend.enrollment.exception.CourseAdmissionAlreadyChargedException.class);
    }
}
