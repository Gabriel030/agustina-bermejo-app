package com.red_escuela.backend.billing.collections.service;

import com.red_escuela.backend.academicstructure.entity.Course;
import com.red_escuela.backend.academicstructure.entity.Institution;
import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.academicstructure.repository.CourseRepository;
import com.red_escuela.backend.academicstructure.repository.InstitutionRepository;
import com.red_escuela.backend.academicstructure.repository.TermRepository;
import com.red_escuela.backend.billing.collections.operation.OverdueInvoiceDetectionStep;
import com.red_escuela.backend.billing.configuration.repository.ConfigurationVersionRepository;
import com.red_escuela.backend.billing.invariant.BillingInvariantChecker;
import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.billing.invoice.domain.InvoicePaymentStatus;
import com.red_escuela.backend.billing.invoice.domain.event.InvoiceMarkedAsOverdueEvent;
import com.red_escuela.backend.billing.invoice.repository.InvoiceRepository;
import com.red_escuela.backend.billing.service.InvoiceOverdueSweepService;
import com.red_escuela.backend.billing.service.LatePaymentTestFixtures;
import com.red_escuela.backend.billing.support.BillingTestFixtures;
import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.enrollment.entity.CourseEnrollment;
import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.enrollment.repository.CourseEnrollmentRepository;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.identity.entity.StudentOnboardingStatus;
import com.red_escuela.backend.identity.repository.AddressRepository;
import com.red_escuela.backend.identity.repository.PersonRepository;
import com.red_escuela.backend.identity.repository.StudentRepository;
import com.red_escuela.backend.shared.domain.Address;
import com.red_escuela.backend.shared.domain.StatusType;
import com.red_escuela.backend.shared.domain.statemachine.StatusChangeActor;
import com.red_escuela.backend.shared.time.MutableClock;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static com.red_escuela.backend.shared.domain.TestMoney.ars;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@RecordApplicationEvents
class OverdueInvoiceDetectionTest extends AbstractIntegrationTest {
    private static final StatusChangeActor TEST_ACTOR = new StatusChangeActor(null, "OverdueInvoiceDetectionTest");

    @Autowired
    private OverdueInvoiceTransitionService transitionService;
    @Autowired
    private OverdueInvoiceDetectionStep detectionStep;
    @Autowired
    private InvoiceOverdueSweepService sweepService;
    @Autowired
    private InvoiceRepository invoiceRepository;
    @Autowired
    private ConfigurationVersionRepository configurationVersionRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private ApplicationEvents applicationEvents;
    @Autowired
    private AddressRepository addressRepository;
    @Autowired
    private PersonRepository personRepository;
    @Autowired
    private InstitutionRepository institutionRepository;
    @Autowired
    private StudentRepository studentRepository;
    @Autowired
    private CourseRepository courseRepository;
    @Autowired
    private CourseEnrollmentRepository courseEnrollmentRepository;
    @Autowired
    private TermRepository termRepository;

    private Person person;
    private CourseEnrollment enrollment;

    private void arrange() {
        String username = "overdue.detection." + System.nanoTime();
        Address address = addressRepository.save(new Address());
        Person created = new Person();
        created.setUsername(username);
        created.setDni(Long.toString(Math.abs((long) username.hashCode())));
        created.setName("Persona");
        created.setLastname("Prueba");
        created.setEmail(username + "@example.com");
        created.setAddress(address);
        created.setStatus(StatusType.ACTIVE);
        person = personRepository.save(created);
        Institution institution = new Institution();
        institution.setName("Instituto de prueba " + System.nanoTime());
        institution = institutionRepository.save(institution);
        Student student = new Student();
        student.setPerson(person);
        student.setInstitution(institution);
        student.setOnboardingStatus(StudentOnboardingStatus.CONFIRMADO);
        student = studentRepository.save(student);
        Term term = new Term();
        term.setYear(2026);
        term.setStatus(StatusType.ACTIVE);
        term = termRepository.save(term);
        Course course = new Course();
        course.setName("Curso de prueba " + System.nanoTime());
        course.setTerm(term);
        course.setIsActive(StatusType.ACTIVE);
        course = courseRepository.save(course);
        CourseEnrollment courseEnrollment = new CourseEnrollment();
        courseEnrollment.setStudent(student);
        courseEnrollment.setCourse(course);
        courseEnrollment.setStatus(EnrollmentStatus.ENROLLED);
        courseEnrollment.setEnrollmentDate(LocalDateTime.now());
        enrollment = courseEnrollmentRepository.save(courseEnrollment);
    }

    private void publishGrace(int graceDays, LocalDate effectiveFrom) {
        LatePaymentTestFixtures.persistLatePayment(configurationVersionRepository, person, graceDays, BigDecimal.valueOf(0.05), 30, 2, effectiveFrom);
    }

    private Invoice invoiceDueOn(LocalDate dueDate) {
        return BillingTestFixtures.invoice().forCourseEnrollment(enrollment).forPeriod(YearMonth.from(dueDate)).dueOn(dueDate).withBaseAmount(ars(80000)).build();
    }

    private InvoicePaymentStatus statusOf(Invoice invoice) {
        return invoiceRepository.findById(invoice.getId()).orElseThrow().getPaymentStatus();
    }

    private void detectOn(Invoice invoice, LocalDate today) {
        transitionService.markAsOverdueWhenPastDueDate(invoice.getId(), today);
    }

    private long overdueEventsOf(Invoice invoice) {
        return applicationEvents.stream(InvoiceMarkedAsOverdueEvent.class).filter(event -> event.invoiceId().equals(invoice.getId())).count();
    }

    @Test
    @Tag("D-057")
    @Tag("D-288")
    void aTwelfthOfMarchInstallmentWithFiveGraceDaysIsOverdueOnTheEighteenth() {
        arrange();
        publishGrace(5, LocalDate.of(2041, 1, 1));
        Invoice invoice = invoiceDueOn(LocalDate.of(2041, 3, 12));

        detectOn(invoice, LocalDate.of(2041, 3, 17));
        assertThat(statusOf(invoice)).isEqualTo(InvoicePaymentStatus.PENDING);
        detectOn(invoice, LocalDate.of(2041, 3, 18));
        assertThat(statusOf(invoice)).isEqualTo(InvoicePaymentStatus.OVERDUE);
    }

    @Test
    @Tag("D-182")
    @Tag("D-288")
    void aGraceChangeAppliesOnlyToInstallmentsDueFromItsEffectiveDate() {
        arrange();
        publishGrace(5, LocalDate.of(2042, 1, 1));
        publishGrace(3, LocalDate.of(2042, 3, 14));
        Invoice dueBeforeTheChange = invoiceDueOn(LocalDate.of(2042, 3, 12));
        Invoice dueAfterTheChange = invoiceDueOn(LocalDate.of(2042, 3, 15));

        detectOn(dueBeforeTheChange, LocalDate.of(2042, 3, 17));
        detectOn(dueAfterTheChange, LocalDate.of(2042, 3, 17));
        assertThat(statusOf(dueBeforeTheChange)).isEqualTo(InvoicePaymentStatus.PENDING);
        detectOn(dueBeforeTheChange, LocalDate.of(2042, 3, 18));
        detectOn(dueAfterTheChange, LocalDate.of(2042, 3, 18));
        assertThat(statusOf(dueBeforeTheChange)).isEqualTo(InvoicePaymentStatus.OVERDUE);
        assertThat(statusOf(dueAfterTheChange)).isEqualTo(InvoicePaymentStatus.PENDING);
        detectOn(dueAfterTheChange, LocalDate.of(2042, 3, 19));
        assertThat(statusOf(dueAfterTheChange)).isEqualTo(InvoicePaymentStatus.OVERDUE);
    }

    @Test
    @Tag("D-288")
    void theVersionStartingOnTheDueDateRulesAndTheOneStartingTheNextDayDoesNot() {
        arrange();
        publishGrace(1, LocalDate.of(2043, 1, 1));
        publishGrace(10, LocalDate.of(2043, 3, 12));
        publishGrace(0, LocalDate.of(2043, 3, 13));
        Invoice dueTheDayBefore = invoiceDueOn(LocalDate.of(2043, 3, 11));
        Invoice dueOnTheStartDay = invoiceDueOn(LocalDate.of(2043, 3, 12));
        Invoice dueOnTheNextVersionDay = invoiceDueOn(LocalDate.of(2043, 3, 13));

        for (Invoice invoice : List.of(dueTheDayBefore, dueOnTheStartDay, dueOnTheNextVersionDay)) {
            detectOn(invoice, LocalDate.of(2043, 3, 13));
        }
        assertThat(statusOf(dueTheDayBefore)).isEqualTo(InvoicePaymentStatus.OVERDUE);
        assertThat(statusOf(dueOnTheStartDay)).isEqualTo(InvoicePaymentStatus.PENDING);
        assertThat(statusOf(dueOnTheNextVersionDay)).isEqualTo(InvoicePaymentStatus.PENDING);

        for (Invoice invoice : List.of(dueOnTheStartDay, dueOnTheNextVersionDay)) {
            detectOn(invoice, LocalDate.of(2043, 3, 14));
        }
        assertThat(statusOf(dueOnTheStartDay)).isEqualTo(InvoicePaymentStatus.PENDING);
        assertThat(statusOf(dueOnTheNextVersionDay)).isEqualTo(InvoicePaymentStatus.OVERDUE);
        detectOn(dueOnTheStartDay, LocalDate.of(2043, 3, 22));
        assertThat(statusOf(dueOnTheStartDay)).isEqualTo(InvoicePaymentStatus.PENDING);
        detectOn(dueOnTheStartDay, LocalDate.of(2043, 3, 23));
        assertThat(statusOf(dueOnTheStartDay)).isEqualTo(InvoicePaymentStatus.OVERDUE);
    }

    @Test
    @Tag("D-182")
    @Tag("D-288")
    void aShorterGraceLoadedAfterTheDueDateDoesNotMakeAnIssuedInstallmentOverdueEarly() {
        arrange();
        publishGrace(5, LocalDate.of(2044, 1, 1));
        publishGrace(0, LocalDate.of(2044, 3, 15));
        Invoice invoice = invoiceDueOn(LocalDate.of(2044, 3, 12));

        detectOn(invoice, LocalDate.of(2044, 3, 16));
        assertThat(statusOf(invoice)).isEqualTo(InvoicePaymentStatus.PENDING);
        detectOn(invoice, LocalDate.of(2044, 3, 18));
        assertThat(statusOf(invoice)).isEqualTo(InvoicePaymentStatus.OVERDUE);
    }

    @Test
    @Tag("D-153")
    void anInstallmentUnderPaymentReviewNeverBecomesOverdueAndEmitsNoEvent() {
        arrange();
        publishGrace(0, LocalDate.of(2045, 1, 1));
        LocalDate dueDate = LocalDate.of(2045, 3, 1);
        Invoice invoice = BillingTestFixtures.invoice().forCourseEnrollment(enrollment).forPeriod(YearMonth.from(dueDate)).dueOn(dueDate).withBaseAmount(ars(80000))
                .customizedBy(created -> created.markPaymentUnderReview(dueDate.minusDays(1), TEST_ACTOR, "Comprobante subido a tiempo")).build();
        BillingInvariantChecker invariants = BillingInvariantChecker.startingNow(jdbcTemplate);

        for (int day = 2; day <= 20; day++) {
            detectOn(invoice, LocalDate.of(2045, 3, day));
            assertThat(statusOf(invoice)).isEqualTo(InvoicePaymentStatus.PAYMENT_UNDER_REVIEW);
        }
        assertThat(overdueEventsOf(invoice)).isZero();
        invariants.assertAllBillingInvariantsHold();
    }

    @Test
    @Tag("D-153")
    @Tag("D-288")
    void anInstallmentReleasedFromReviewBeforeItsDueDateBecomesOverdueWithItsOriginalDueDate() {
        arrange();
        publishGrace(5, LocalDate.of(2046, 1, 1));
        LocalDate dueDate = LocalDate.of(2046, 3, 12);
        Invoice invoice = BillingTestFixtures.invoice().forCourseEnrollment(enrollment).forPeriod(YearMonth.from(dueDate)).dueOn(dueDate).withBaseAmount(ars(80000))
                .customizedBy(created -> {
                    created.markPaymentUnderReview(LocalDate.of(2046, 3, 10), TEST_ACTOR, "Comprobante subido a tiempo");
                    created.releasePaymentReview(LocalDate.of(2046, 3, 11), 5, TEST_ACTOR, "Comprobante rechazado");
                }).build();

        assertThat(statusOf(invoice)).isEqualTo(InvoicePaymentStatus.PENDING);
        detectOn(invoice, LocalDate.of(2046, 3, 17));
        assertThat(statusOf(invoice)).isEqualTo(InvoicePaymentStatus.PENDING);
        detectOn(invoice, LocalDate.of(2046, 3, 18));
        assertThat(statusOf(invoice)).isEqualTo(InvoicePaymentStatus.OVERDUE);
    }

    @Test
    @Tag("D-288")
    void theBusinessDayIsTheBuenosAiresOneEvenWhenTheJvmRunsInUtc() {
        arrange();
        publishGrace(5, LocalDate.of(2047, 1, 1));
        Invoice invoice = invoiceDueOn(LocalDate.of(2047, 3, 12));

        MutableClock.INSTANCE.setInstant(Instant.parse("2047-03-18T01:00:00Z"));
        sweepService.run();
        assertThat(statusOf(invoice)).isEqualTo(InvoicePaymentStatus.PENDING);

        MutableClock.INSTANCE.setInstant(Instant.parse("2047-03-18T03:30:00Z"));
        sweepService.run();
        assertThat(statusOf(invoice)).isEqualTo(InvoicePaymentStatus.OVERDUE);
    }

    @Test
    @Tag("D-288")
    void runningTheSweepTwiceOrAfterDaysWithoutRunningMarksEachInstallmentOverdueOnce() {
        arrange();
        publishGrace(5, LocalDate.of(2048, 1, 1));
        Invoice invoice = invoiceDueOn(LocalDate.of(2048, 3, 12));

        detectionStep.detectOverdueInvoices(LocalDate.of(2048, 3, 17));
        assertThat(statusOf(invoice)).isEqualTo(InvoicePaymentStatus.PENDING);
        detectionStep.detectOverdueInvoices(LocalDate.of(2048, 3, 25));
        detectionStep.detectOverdueInvoices(LocalDate.of(2048, 3, 25));
        detectionStep.detectOverdueInvoices(LocalDate.of(2048, 3, 26));

        assertThat(statusOf(invoice)).isEqualTo(InvoicePaymentStatus.OVERDUE);
        assertThat(overdueEventsOf(invoice)).isEqualTo(1);
        Integer transitions = jdbcTemplate.queryForObject(
                "select count(*) from invoice_status_change where invoice_id = ? and status_kind = 'PAYMENT' and to_status = 'OVERDUE'", Integer.class, invoice.getId());
        assertThat(transitions).isEqualTo(1);
    }

    @Test
    @Tag("D-288")
    void twoThreadsDetectingTheSameInstallmentProduceASingleTransition() throws Exception {
        arrange();
        publishGrace(5, LocalDate.of(2049, 1, 1));
        Invoice invoice = invoiceDueOn(LocalDate.of(2049, 3, 12));
        int threads = 6;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Boolean>> results = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            results.add(executor.submit(() -> {
                ready.countDown();
                go.await();
                return transitionService.markAsOverdueWhenPastDueDate(invoice.getId(), LocalDate.of(2049, 3, 20));
            }));
        }
        ready.await();
        go.countDown();
        int marked = 0;
        for (Future<Boolean> result : results) {
            if (result.get()) {
                marked++;
            }
        }
        executor.shutdown();

        assertThat(marked).isEqualTo(1);
        Integer transitions = jdbcTemplate.queryForObject(
                "select count(*) from invoice_status_change where invoice_id = ? and status_kind = 'PAYMENT' and to_status = 'OVERDUE'", Integer.class, invoice.getId());
        assertThat(transitions).isEqualTo(1);
    }

    @Test
    @Tag("D-057")
    @Tag("D-288")
    void paidAndAlreadyOverdueInstallmentsAreLeftUntouched() {
        arrange();
        publishGrace(0, LocalDate.of(2050, 1, 1));
        Invoice paid = BillingTestFixtures.invoice().forCourseEnrollment(enrollment).forPeriod(YearMonth.of(2050, 2)).dueOn(LocalDate.of(2050, 2, 10)).paid().build();
        Invoice overdue = BillingTestFixtures.invoice().forCourseEnrollment(enrollment).forPeriod(YearMonth.of(2050, 3)).dueOn(LocalDate.of(2050, 3, 10)).overdue().build();

        detectOn(paid, LocalDate.of(2050, 4, 1));
        detectOn(overdue, LocalDate.of(2050, 4, 1));

        assertThat(statusOf(paid)).isEqualTo(InvoicePaymentStatus.PAID);
        assertThat(statusOf(overdue)).isEqualTo(InvoicePaymentStatus.OVERDUE);
        assertThat(overdueEventsOf(overdue)).isZero();
    }

    @Test
    @Tag("D-288")
    void aDayByDayRunKeepsEveryBillingInvariantAndFollowsEachInstallmentsOwnVersion() {
        arrange();
        publishGrace(5, LocalDate.of(2051, 1, 1));
        publishGrace(3, LocalDate.of(2051, 3, 14));
        Invoice early = invoiceDueOn(LocalDate.of(2051, 3, 12));
        Invoice late = invoiceDueOn(LocalDate.of(2051, 3, 15));
        BillingInvariantChecker invariants = BillingInvariantChecker.startingNow(jdbcTemplate);

        for (int day = 10; day <= 25; day++) {
            LocalDate today = LocalDate.of(2051, 3, day);
            detectOn(early, today);
            detectOn(late, today);
            assertThat(statusOf(early)).isEqualTo(day >= 18 ? InvoicePaymentStatus.OVERDUE : InvoicePaymentStatus.PENDING);
            assertThat(statusOf(late)).isEqualTo(day >= 19 ? InvoicePaymentStatus.OVERDUE : InvoicePaymentStatus.PENDING);
            invariants.assertAllBillingInvariantsHold();
        }
    }
}
