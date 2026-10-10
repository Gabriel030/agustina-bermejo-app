package com.red_escuela.backend.billing.service;

import com.red_escuela.backend.academicstructure.entity.Course;
import com.red_escuela.backend.academicstructure.entity.Institution;
import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.billing.entity.EnrollmentPaymentStatus;
import com.red_escuela.backend.billing.entity.PaymentPlanCadence;
import com.red_escuela.backend.billing.entity.PaymentPlanOption;
import com.red_escuela.backend.billing.entity.PaymentPlanSelection;
import com.red_escuela.backend.billing.entity.PriceRevision;
import com.red_escuela.backend.billing.invoice.domain.FeeType;
import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.billing.invoice.repository.InvoiceRepository;
import com.red_escuela.backend.billing.note.domain.CancelInvoiceCommand;
import com.red_escuela.backend.billing.note.domain.CreditNoteReason;
import com.red_escuela.backend.billing.note.service.CancelInvoiceOperation;
import com.red_escuela.backend.billing.operation.BillingOperationPipeline;
import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.enrollment.entity.CourseEnrollment;
import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.identity.entity.StudentOnboardingStatus;
import com.red_escuela.backend.shared.domain.Address;
import com.red_escuela.backend.shared.domain.StatusType;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;

import static com.red_escuela.backend.shared.domain.TestMoney.ars;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Tag("D-202")
@Tag("D-245")
class CancelledCourseChargeReissueIT extends AbstractIntegrationTest {
    @Autowired private BillingTestTrigger trigger;
    @Autowired private InvoiceRepository invoiceRepository;
    @Autowired private RecurringFeeWindowService windowService;
    @Autowired private BillingOperationPipeline pipeline;
    @Autowired private CancelInvoiceOperation cancelOperation;
    @Autowired private TransactionTemplate transactionTemplate;
    @Autowired private EntityManager entityManager;

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

    private Long enrolledInCourseWithThreeInstallments() {
        return transactionTemplate.execute(status -> {
            Institution institution = new Institution();
            institution.setName("Instituto " + System.nanoTime());
            entityManager.persist(institution);
            Student student = new Student();
            student.setPerson(persistPerson("8" + System.nanoTime()));
            student.setInstitution(institution);
            student.setOnboardingStatus(StudentOnboardingStatus.CONFIRMADO);
            entityManager.persist(student);
            Term term = new Term();
            term.setYear(YearMonth.now().getYear());
            term.setStatus(StatusType.ACTIVE);
            entityManager.persist(term);
            Course course = new Course();
            course.setName("Curso " + System.nanoTime());
            course.setTerm(term);
            course.setDurationMonths(3);
            course.setIsActive(StatusType.ACTIVE);
            entityManager.persist(course);
            Person director = persistPerson("9" + System.nanoTime());
            PriceRevision revision = new PriceRevision();
            revision.setCourse(course);
            revision.setFeeType(FeeType.MONTHLY_INSTALLMENT);
            revision.setDescription("Cuota");
            revision.setMonthlyAmount(ars(1000));
            revision.setEffectiveFrom(java.time.LocalDate.now().minusYears(1));
            revision.setCreatedBy(director);
            entityManager.persist(revision);
            PaymentPlanOption option = new PaymentPlanOption();
            option.setCourse(course);
            option.setDescription("Mensual");
            option.setCadence(PaymentPlanCadence.MONTHLY);
            option.setCreatedBy(director);
            entityManager.persist(option);
            CourseEnrollment enrollment = new CourseEnrollment();
            enrollment.setStudent(student);
            enrollment.setCourse(course);
            enrollment.setStatus(EnrollmentStatus.ENROLLED);
            enrollment.setEnrollmentDate(LocalDateTime.now());
            enrollment.setPaymentStatus(EnrollmentPaymentStatus.UP_TO_DATE);
            entityManager.persist(enrollment);
            PaymentPlanSelection selection = new PaymentPlanSelection();
            selection.setCourseEnrollment(enrollment);
            selection.setPaymentPlanOption(option);
            selection.setEffectiveFrom(YearMonth.now().minusMonths(3));
            selection.setCreatedBy(director);
            entityManager.persist(selection);
            return enrollment.getId();
        });
    }

    private void generate(Long enrollmentId) {
        transactionTemplate.executeWithoutResult(status ->
                trigger.generateNextInstallmentForCourse(entityManager.find(CourseEnrollment.class, enrollmentId), YearMonth.now()));
    }

    private Integer remaining(Long enrollmentId) {
        return transactionTemplate.execute(status -> windowService.remainingInvoices(entityManager.find(CourseEnrollment.class, enrollmentId)));
    }

    @Test
    @Tag("B2-14")
    @Tag("D-009")
    void courseInstallmentCancelledByBillingErrorIsReissuedAndCountsOnce() {
        Long enrollment = enrolledInCourseWithThreeInstallments();
        generate(enrollment);
        Invoice first = invoiceRepository.findByCourseEnrollmentIdOrderByPeriod(enrollment).get(0);
        String key = first.getPrimaryLine().getChargeKey();
        assertThat(remaining(enrollment)).isEqualTo(2);
        Long director = transactionTemplate.execute(status -> persistPerson("5" + System.nanoTime()).getId());

        pipeline.runOperation(cancelOperation, CancelInvoiceCommand.byPerson(first.getId(), CreditNoteReason.BILLING_ERROR, "Error de facturación", director, true));
        assertThat(remaining(enrollment)).isEqualTo(3);
        generate(enrollment);

        List<Invoice> all = invoiceRepository.findByCourseEnrollmentIdOrderByPeriod(enrollment);
        assertThat(all).hasSize(2);
        assertThat(all).filteredOn(i -> !i.getPrimaryLine().getChargeKeyReleased()).singleElement()
                .satisfies(live -> assertThat(live.getPrimaryLine().getChargeKey()).isEqualTo(key));
        assertThat(remaining(enrollment)).isEqualTo(2);
    }
}
