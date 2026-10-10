package com.red_escuela.backend.billing.collections.service;

import com.red_escuela.backend.academicstructure.entity.Course;
import com.red_escuela.backend.academicstructure.entity.Institution;
import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.academicstructure.repository.CourseRepository;
import com.red_escuela.backend.academicstructure.repository.InstitutionRepository;
import com.red_escuela.backend.academicstructure.repository.TermRepository;
import com.red_escuela.backend.billing.account.service.StudentCurrentAccountService;
import com.red_escuela.backend.billing.collections.operation.OverdueInvoiceDetectionStep;
import com.red_escuela.backend.billing.collections.repository.LateFeeChargeRepository;
import com.red_escuela.backend.billing.configuration.repository.ConfigurationVersionRepository;
import com.red_escuela.backend.billing.events.LateFeeIssuedEvent;
import com.red_escuela.backend.billing.invariant.BillingInvariantChecker;
import com.red_escuela.backend.billing.invoice.domain.FeeType;
import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.billing.invoice.domain.InvoicePaymentStatus;
import com.red_escuela.backend.billing.invoice.repository.InvoiceRepository;
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
import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.shared.domain.Money;
import com.red_escuela.backend.shared.domain.NonNegativeMoney;
import com.red_escuela.backend.shared.domain.StatusType;
import com.red_escuela.backend.shared.domain.statemachine.StatusChangeActor;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.TransactionTemplate;
import org.mockito.Mockito;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static com.red_escuela.backend.shared.domain.TestMoney.ars;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest
@ActiveProfiles("test")
@RecordApplicationEvents
class LateFeeAccrualIT extends AbstractIntegrationTest {
    private static final StatusChangeActor ACTOR = new StatusChangeActor(null, "LateFeeAccrualIT");
    private static final BigDecimal FIVE_PERCENT = new BigDecimal("0.05");

    @Autowired
    private LateFeeAccrualService accrualService;
    @Autowired
    private OverdueInvoiceTransitionService transitionService;
    @Autowired
    private OverdueInvoiceDetectionStep detectionStep;
    @Autowired
    private InvoiceRepository invoiceRepository;
    @Autowired
    private ConfigurationVersionRepository configurationVersionRepository;
    @Autowired
    private JdbcTemplate jdbc;
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
    @Autowired
    private StudentCurrentAccountService accountService;
    @Autowired
    private TransactionTemplate transactionTemplate;
    @MockitoSpyBean
    private LateFeeChargeRepository chargeRepositorySpy;

    private Person person;
    private CourseEnrollment enrollment;

    private void arrange(int year) {
        String username = "late.fee." + year + "." + System.nanoTime();
        Address address = addressRepository.save(new Address());
        Person created = new Person();
        created.setUsername(username);
        created.setDni(Long.toString(Math.abs((long) username.hashCode())));
        created.setName("Persona");
        created.setLastname("Recargo");
        created.setEmail(username + "@example.com");
        created.setAddress(address);
        created.setStatus(StatusType.ACTIVE);
        person = personRepository.save(created);
        Institution institution = new Institution();
        institution.setName("Instituto recargo " + System.nanoTime());
        institution = institutionRepository.save(institution);
        Student student = new Student();
        student.setPerson(person);
        student.setInstitution(institution);
        student.setOnboardingStatus(StudentOnboardingStatus.CONFIRMADO);
        student = studentRepository.save(student);
        Term term = new Term();
        term.setYear(year);
        term.setStatus(StatusType.ACTIVE);
        term = termRepository.save(term);
        Course course = new Course();
        course.setName("Curso recargo " + System.nanoTime());
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

    private void publish(int graceDays, String rate, int ndDays, boolean freeze, boolean enabled, LocalDate effectiveFrom) {
        LatePaymentTestFixtures.persistLatePayment(configurationVersionRepository, person, graceDays, new BigDecimal(rate), 30, 1, ndDays, freeze, enabled, effectiveFrom);
    }

    private void publishDefaults(int year) {
        publish(5, "0.05", 10, true, true, LocalDate.of(year, 1, 1));
    }

    private Invoice installmentDueOn(LocalDate dueDate, Money amount) {
        return BillingTestFixtures.invoice().forCourseEnrollment(enrollment).forPeriod(YearMonth.from(dueDate)).dueOn(dueDate).withBaseAmount(amount).overdue().build();
    }

    private Invoice pendingInstallmentDueOn(LocalDate dueDate) {
        return BillingTestFixtures.invoice().forCourseEnrollment(enrollment).forPeriod(YearMonth.from(dueDate)).dueOn(dueDate).withBaseAmount(ars(80000)).build();
    }

    private List<Map<String, Object>> chargesOf(Invoice origin) {
        return jdbc.queryForList("select c.*, d.total as nd_total, d.due_date as nd_due, d.issue_date as nd_issue, d.invoice_number as nd_number, d.document_type, d.invoice_type, d.origin_invoice_id "
                + "from late_fee_charge c join invoice d on d.id = c.debit_note_id where c.origin_invoice_id = ? order by c.month_ordinal", origin.getId());
    }

    private void reconcile(Invoice origin, LocalDate date) {
        accrualService.reconcileLateFeesOfInvoice(origin.getId(), date);
    }

    private Invoice reload(Invoice invoice) {
        return invoiceRepository.findInvoiceWithLinesById(invoice.getId()).orElseThrow();
    }

    @Test
    @Tag("D-060")
    @Tag("D-061")
    @Tag("D-244")
    void firstLateFeeIsADebitNoteLinkedToTheOriginWithItsFactDate() {
        arrange(2061);
        publishDefaults(2061);
        Invoice origin = installmentDueOn(LocalDate.of(2061, 3, 12), ars(80000));
        BillingInvariantChecker invariants = BillingInvariantChecker.startingNow(jdbc);

        reconcile(origin, LocalDate.of(2061, 3, 17));
        assertThat(chargesOf(origin)).isEmpty();
        reconcile(origin, LocalDate.of(2061, 3, 18));

        List<Map<String, Object>> charges = chargesOf(origin);
        assertThat(charges).hasSize(1);
        Map<String, Object> charge = charges.get(0);
        assertThat(charge.get("nd_total")).isEqualTo(new BigDecimal("4000.00"));
        assertThat(charge.get("document_type")).isEqualTo("DEBIT_NOTE");
        assertThat(charge.get("invoice_type")).isEqualTo("LATE_FEE");
        assertThat((String) charge.get("nd_number")).startsWith("ND-");
        assertThat(charge.get("origin_invoice_id")).isEqualTo(origin.getId());
        assertThat(charge.get("month_ordinal")).isEqualTo(1);
        assertThat(charge.get("fact_date").toString()).isEqualTo("2061-03-18");
        assertThat((BigDecimal) charge.get("monthly_rate")).isEqualByComparingTo("0.05");
        assertThat((BigDecimal) charge.get("base_amount")).isEqualByComparingTo("80000.00");
        assertThat(charge.get("late_payment_version_id")).isNotNull();
        assertThat(jdbc.queryForObject("select count(*) from current_account_entry where entry_type = 'DEBIT_NOTE_ISSUED' and student_current_account_id = ?", Long.class,
                reload(origin).getStudentCurrentAccountId())).isEqualTo(1L);
        Invoice reloaded = reload(origin);
        assertThat(reloaded.getAmounts().total().value().amount()).isEqualByComparingTo("80000.00");
        assertThat(jdbc.queryForObject("select student_snapshot::text from invoice where id = ?", String.class, origin.getId()))
                .isEqualTo(jdbc.queryForObject("select student_snapshot::text from invoice where id = ?", String.class, charge.get("debit_note_id")));
        invariants.assertAllBillingInvariantsHold();
    }

    @Test
    @Tag("D-239")
    @Tag("D-061")
    void lateFeeAccruesEveryMonthWithoutCapAndNeverOverRecharges() {
        arrange(2062);
        publishDefaults(2062);
        Invoice origin = pendingInstallmentDueOn(LocalDate.of(2062, 3, 12));
        BillingInvariantChecker invariants = BillingInvariantChecker.startingNow(jdbc);

        LocalDate day = LocalDate.of(2062, 3, 12);
        LocalDate end = LocalDate.of(2063, 5, 17);
        int dayCount = 0;
        while (!day.isAfter(end)) {
            transitionService.markAsOverdueWhenPastDueDate(origin.getId(), day);
            reconcile(origin, day);
            if (++dayCount % 20 == 0) {
                invariants.assertAllBillingInvariantsHold();
            }
            day = day.plusDays(1);
        }
        invariants.assertAllBillingInvariantsHold();

        List<Map<String, Object>> charges = chargesOf(origin);
        assertThat(charges).hasSize(14);
        assertThat(charges).allSatisfy(charge -> assertThat((BigDecimal) charge.get("nd_total")).isEqualByComparingTo("4000.00"));
        assertThat(charges.stream().map(charge -> (Integer) charge.get("month_ordinal")).toList()).containsExactly(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14);
        assertThat(jdbc.queryForObject("select count(*) from invoice_line where charge_key like ?", Long.class, "LF:" + origin.getId() + ":M%")).isEqualTo(14L);
    }

    @Test
    @Tag("D-297")
    void lateFeeBaseIsTheLinePriceBeforeAnyScholarshipAndTheChargeKeepsThePhoto() {
        arrange(2064);
        publishDefaults(2064);
        Invoice origin = BillingTestFixtures.invoice().forCourseEnrollment(enrollment).forPeriod(YearMonth.of(2064, 3)).dueOn(LocalDate.of(2064, 3, 12))
                .withBaseAmount(ars(100000)).withScholarshipOf(ars(50000)).overdue().build();

        reconcile(origin, LocalDate.of(2064, 3, 18));

        Map<String, Object> charge = chargesOf(origin).get(0);
        assertThat(charge.get("nd_total")).isEqualTo(new BigDecimal("5000.00"));
        assertThat((BigDecimal) charge.get("base_amount")).isEqualByComparingTo("100000.00");
        Invoice reloaded = reload(origin);
        assertThat(reloaded.getPrimaryLine().getAmount().value().amount()).isEqualByComparingTo("100000.00");
        assertThat(reloaded.getPrimaryLine().calculateNetAmount().value().amount()).isEqualByComparingTo("50000.00");
    }

    @Test
    @Tag("D-288")
    @Tag("D-182")
    void allLateFeesOfAnInvoiceUseTheTermsInForceOnItsDueDate() {
        arrange(2065);
        publish(5, "0.05", 10, true, true, LocalDate.of(2065, 1, 1));
        publish(3, "0.07", 15, true, true, LocalDate.of(2065, 4, 1));
        Invoice march = installmentDueOn(LocalDate.of(2065, 3, 10), ars(80000));
        Invoice april = installmentDueOn(LocalDate.of(2065, 4, 10), ars(80000));

        reconcile(march, LocalDate.of(2065, 6, 20));
        reconcile(april, LocalDate.of(2065, 6, 20));

        List<Map<String, Object>> marchCharges = chargesOf(march);
        assertThat(marchCharges.stream().map(c -> c.get("fact_date").toString()).toList()).containsExactly("2065-03-16", "2065-04-16", "2065-05-16", "2065-06-16");
        assertThat(marchCharges).allSatisfy(c -> {
            assertThat((BigDecimal) c.get("nd_total")).isEqualByComparingTo("4000.00");
            assertThat(java.time.temporal.ChronoUnit.DAYS.between(LocalDate.parse(c.get("nd_issue").toString()), LocalDate.parse(c.get("nd_due").toString()))).isEqualTo(10L);
        });
        List<Map<String, Object>> aprilCharges = chargesOf(april);
        assertThat(aprilCharges.stream().map(c -> c.get("fact_date").toString()).toList()).containsExactly("2065-04-14", "2065-05-14", "2065-06-14");
        assertThat(aprilCharges).allSatisfy(c -> {
            assertThat((BigDecimal) c.get("nd_total")).isEqualByComparingTo("5600.00");
            assertThat(java.time.temporal.ChronoUnit.DAYS.between(LocalDate.parse(c.get("nd_issue").toString()), LocalDate.parse(c.get("nd_due").toString()))).isEqualTo(15L);
        });
    }

    @Test
    @Tag("D-294")
    @Tag("D-210")
    void chargingEnablementIsEvaluatedOnEachFactDateAndPastMonthsAreNeverCharged() {
        arrange(2066);
        publish(5, "0.05", 10, true, false, LocalDate.of(2066, 1, 1));
        publish(5, "0.06", 10, true, true, LocalDate.of(2066, 7, 1));
        Invoice origin = installmentDueOn(LocalDate.of(2066, 3, 12), ars(80000));

        reconcile(origin, LocalDate.of(2066, 6, 30));
        assertThat(chargesOf(origin)).isEmpty();
        reconcile(origin, LocalDate.of(2066, 7, 2));
        assertThat(chargesOf(origin)).isEmpty();
        reconcile(origin, LocalDate.of(2066, 7, 18));
        reconcile(origin, LocalDate.of(2066, 8, 20));

        List<Map<String, Object>> charges = chargesOf(origin);
        assertThat(charges.stream().map(c -> (Integer) c.get("month_ordinal")).toList()).containsExactly(5, 6);
        assertThat(charges).allSatisfy(c -> assertThat((BigDecimal) c.get("nd_total")).isEqualByComparingTo("4000.00"));
    }

    @Test
    @Tag("D-210")
    void disabledChargingIssuesNothing() {
        arrange(2067);
        publish(5, "0.05", 10, true, false, LocalDate.of(2067, 1, 1));
        Invoice origin = installmentDueOn(LocalDate.of(2067, 3, 12), ars(80000));

        reconcile(origin, LocalDate.of(2067, 9, 1));

        assertThat(chargesOf(origin)).isEmpty();
        assertThat(jdbc.queryForObject("select count(*) from invoice where origin_invoice_id = ?", Long.class, origin.getId())).isZero();
    }

    @Test
    @Tag("D-149")
    @Tag("D-057")
    void debitNoteDueDateIsIssueDatePlusTermsEvenWhenRecoveredLate() {
        arrange(2068);
        publishDefaults(2068);
        Invoice onTime = installmentDueOn(LocalDate.of(2068, 3, 12), ars(80000));
        Invoice recovered = installmentDueOn(LocalDate.of(2068, 3, 12), ars(80000));
        BillingInvariantChecker invariants = BillingInvariantChecker.startingNow(jdbc);

        reconcile(onTime, LocalDate.of(2068, 3, 18));
        reconcile(recovered, LocalDate.of(2068, 4, 21));

        assertThat(chargesOf(onTime).get(0).get("nd_due").toString()).isEqualTo("2068-03-28");
        List<Map<String, Object>> recoveredCharges = chargesOf(recovered);
        assertThat(recoveredCharges).hasSize(2);
        assertThat(recoveredCharges.get(0).get("nd_due").toString()).isEqualTo("2068-05-01");
        assertThat(recoveredCharges.get(0).get("fact_date").toString()).isEqualTo("2068-03-18");
        assertThat(recoveredCharges.get(1).get("fact_date").toString()).isEqualTo("2068-04-18");
        assertThat(recoveredCharges).allSatisfy(c -> assertThat(c.get("nd_issue").toString()).isEqualTo("2068-04-21"));
        assertThat(jdbc.queryForList("select charge_key from invoice_line where invoice_id in (?, ?) order by charge_key", String.class, recoveredCharges.get(0).get("debit_note_id"),
                recoveredCharges.get(1).get("debit_note_id"))).containsExactly("LF:" + recovered.getId() + ":M1", "LF:" + recovered.getId() + ":M2");
        invariants.assertAllBillingInvariantsHold();
    }

    @Test
    @Tag("D-325")
    @Tag("D-244")
    void aLateFeeRecoveredMoreThanFifteenDaysAfterItsFactDateIsFlaggedForSecretariat() {
        arrange(2069);
        publishDefaults(2069);
        Invoice origin = installmentDueOn(LocalDate.of(2069, 3, 12), ars(80000));

        reconcile(origin, LocalDate.of(2069, 4, 21));

        List<LateFeeIssuedEvent> events = applicationEvents.stream(LateFeeIssuedEvent.class).filter(e -> e.originInvoiceId().equals(origin.getId())).toList();
        assertThat(events).hasSize(2);
        assertThat(events.get(0).factDate()).isEqualTo(LocalDate.of(2069, 3, 18));
        assertThat(events.get(0).issuedAfterFiscalWindow()).isTrue();
        assertThat(events.get(1).issuedAfterFiscalWindow()).isFalse();
    }

    @Test
    @Tag("D-149")
    void aDebitNoteBecomesOverdueButNeverAccruesALateFeeOfItsOwn() {
        arrange(2070);
        publishDefaults(2070);
        Invoice origin = installmentDueOn(LocalDate.of(2070, 3, 12), ars(80000));
        reconcile(origin, LocalDate.of(2070, 3, 18));
        Long debitNoteId = (Long) chargesOf(origin).get(0).get("debit_note_id");

        for (LocalDate day = LocalDate.of(2070, 3, 19); !day.isAfter(LocalDate.of(2070, 6, 30)); day = day.plusDays(1)) {
            transitionService.markAsOverdueWhenPastDueDate(debitNoteId, day);
            reconcile(origin, day);
        }
        transitionService.markAsOverdueWhenPastDueDate(debitNoteId, LocalDate.of(2070, 6, 30));
        reconcile(invoiceRepository.findInvoiceWithLinesById(debitNoteId).orElseThrow(), LocalDate.of(2070, 6, 30));

        assertThat(invoiceRepository.findById(debitNoteId).orElseThrow().getPaymentStatus()).isEqualTo(InvoicePaymentStatus.OVERDUE);
        assertThat(jdbc.queryForObject("select count(*) from late_fee_charge where origin_invoice_id = ?", Long.class, debitNoteId)).isZero();
    }

    @Test
    @Tag("D-289")
    @Tag("D-155")
    void onlyInstallmentsAndEnrollmentFeesAccrueAndOtherChargesAreNeverRetried() {
        arrange(2071);
        publishDefaults(2071);
        LocalDate dueDate = LocalDate.of(2071, 3, 12);
        Invoice installment = installmentDueOn(dueDate, ars(80000));
        Invoice enrollmentFee = BillingTestFixtures.invoice().forCourseEnrollment(enrollment).ofType(FeeType.ENROLLMENT_FEE).forPeriod(YearMonth.from(dueDate)).dueOn(dueDate)
                .withBaseAmount(ars(50000)).overdue().build();
        Invoice examFee = BillingTestFixtures.invoice().forCourseEnrollment(enrollment).ofType(FeeType.EXAM_FEE).forPeriod(YearMonth.from(dueDate)).dueOn(dueDate)
                .withBaseAmount(ars(20000)).overdue().build();

        for (Invoice origin : List.of(installment, enrollmentFee, examFee)) {
            reconcile(origin, LocalDate.of(2071, 3, 18));
        }

        assertThat(chargesOf(installment)).hasSize(1);
        assertThat(chargesOf(enrollmentFee)).hasSize(1);
        assertThat(chargesOf(enrollmentFee).get(0).get("nd_total")).isEqualTo(new BigDecimal("2500.00"));
        assertThat(chargesOf(examFee)).isEmpty();
        assertThat(invoiceRepository.findOverdueLateFeeOriginIdsAfter(0L, org.springframework.data.domain.PageRequest.of(0, 100000))).doesNotContain(examFee.getId());
    }

    @Test
    @Tag("D-153")
    void anInvoiceUnderPaymentReviewNeverAccruesALateFee() {
        arrange(2072);
        publishDefaults(2072);
        LocalDate dueDate = LocalDate.of(2072, 3, 12);
        Invoice origin = BillingTestFixtures.invoice().forCourseEnrollment(enrollment).forPeriod(YearMonth.from(dueDate)).dueOn(dueDate).withBaseAmount(ars(80000))
                .customizedBy(created -> created.markPaymentUnderReview(dueDate.minusDays(1), ACTOR, "Comprobante a tiempo")).build();
        BillingInvariantChecker invariants = BillingInvariantChecker.startingNow(jdbc);

        reconcile(origin, LocalDate.of(2072, 5, 30));

        assertThat(chargesOf(origin)).isEmpty();
        assertThat(reload(origin).getPaymentStatus()).isEqualTo(InvoicePaymentStatus.PAYMENT_UNDER_REVIEW);
        invariants.assertAllBillingInvariantsHold();
    }

    @Test
    @Tag("D-154")
    void aReleasedPaymentReviewIssuesPastMonthsAtOnceThroughTheAsyncListener() {
        arrange(2073);
        publishDefaults(2073);
        LocalDate dueDate = LocalDate.of(2073, 3, 10);
        LocalDate releaseDate = LocalDate.of(2073, 4, 20);
        Invoice origin = BillingTestFixtures.invoice().forCourseEnrollment(enrollment).forPeriod(YearMonth.from(dueDate)).dueOn(dueDate).withBaseAmount(ars(80000))
                .customizedBy(created -> {
                    created.markPaymentUnderReview(dueDate.minusDays(1), ACTOR, "Comprobante a tiempo");
                    created.releasePaymentReview(releaseDate, 5, ACTOR, "Comprobante rechazado");
                }).build();

        assertThat(reload(origin).getPaymentStatus()).isEqualTo(InvoicePaymentStatus.OVERDUE);
        accrualService.reconcileLateFeesOfInvoice(origin.getId(), releaseDate);

        assertThat(chargesOf(origin).stream().map(c -> c.get("fact_date").toString()).toList()).containsExactly("2073-03-16", "2073-04-16");
    }

    @Test
    @Tag("D-288")
    void theDetectionEventTriggersTheLateFeeListenerAfterCommit() {
        arrange(2074);
        publishDefaults(2074);
        Invoice origin = pendingInstallmentDueOn(LocalDate.of(2074, 3, 12));

        transitionService.markAsOverdueWhenPastDueDate(origin.getId(), LocalDate.of(2074, 3, 18));

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> assertThat(chargesOf(origin)).hasSize(1));
        assertThat(chargesOf(origin).get(0).get("fact_date").toString()).isEqualTo("2074-03-18");
    }

    @Test
    @Tag("D-061")
    void runningTwiceTheSameDayAndTheListenerTogetherIssueASingleDebitNote() {
        arrange(2075);
        publishDefaults(2075);
        Invoice origin = installmentDueOn(LocalDate.of(2075, 3, 12), ars(80000));

        reconcile(origin, LocalDate.of(2075, 3, 18));
        reconcile(origin, LocalDate.of(2075, 3, 18));
        detectionStep.detectOverdueInvoices(LocalDate.of(2075, 3, 18));

        assertThat(chargesOf(origin)).hasSize(1);
    }

    @Test
    @Tag("D-061")
    void parallelRunsIssueASingleDebitNoteAndNobodyFails() throws Exception {
        arrange(2076);
        publishDefaults(2076);
        Invoice origin = installmentDueOn(LocalDate.of(2076, 3, 12), ars(80000));
        ExecutorService executor = Executors.newFixedThreadPool(6);
        try {
            List<Callable<Integer>> tasks = java.util.stream.IntStream.range(0, 6)
                    .<Callable<Integer>>mapToObj(i -> () -> accrualService.reconcileLateFeesOfInvoice(origin.getId(), LocalDate.of(2076, 3, 18))).toList();
            int issued = 0;
            for (Future<Integer> future : executor.invokeAll(tasks)) {
                issued += future.get();
            }
            assertThat(issued).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
        assertThat(chargesOf(origin)).hasSize(1);
    }

    @Test
    @Tag("D-061")
    void thereIsNoCollisionAcrossTheYearBoundary() {
        arrange(2077);
        publishDefaults(2077);
        Invoice origin = installmentDueOn(LocalDate.of(2077, 12, 10), ars(80000));

        reconcile(origin, LocalDate.of(2078, 2, 20));

        assertThat(chargesOf(origin).stream().map(c -> c.get("fact_date").toString()).toList()).containsExactly("2077-12-16", "2078-01-16", "2078-02-16");
    }

    @Test
    @Tag("D-148")
    @Tag("D-293")
    void lateFeesFreezeWhenTheEnrollmentEndsAndTheEndDayStillCharges() {
        arrange(2078);
        publishDefaults(2078);
        Invoice origin = installmentDueOn(LocalDate.of(2078, 5, 10), ars(80000));
        reconcile(origin, LocalDate.of(2078, 5, 16));
        assertThat(chargesOf(origin)).hasSize(1);
        enrollment.setStatus(EnrollmentStatus.WITHDRAWN);
        enrollment.setCompletionDate(LocalDateTime.of(2078, 7, 16, 10, 0));
        courseEnrollmentRepository.save(enrollment);

        reconcile(origin, LocalDate.of(2078, 9, 30));

        assertThat(chargesOf(origin).stream().map(c -> c.get("fact_date").toString()).toList()).containsExactly("2078-05-16", "2078-06-16", "2078-07-16");
        assertThat(reload(origin).getPaymentStatus()).isEqualTo(InvoicePaymentStatus.OVERDUE);
    }

    @Test
    @Tag("D-148")
    void withFreezingDisabledTheLateFeesKeepRunningAfterTheEnd() {
        arrange(2079);
        publish(5, "0.05", 10, false, true, LocalDate.of(2079, 1, 1));
        Invoice origin = installmentDueOn(LocalDate.of(2079, 5, 10), ars(80000));
        enrollment.setStatus(EnrollmentStatus.WITHDRAWN);
        enrollment.setCompletionDate(LocalDateTime.of(2079, 5, 20, 10, 0));
        courseEnrollmentRepository.save(enrollment);

        reconcile(origin, LocalDate.of(2079, 8, 30));

        assertThat(chargesOf(origin)).hasSize(4);
    }

    @Test
    @Tag("D-097")
    void theDebitNoteFollowsTheCurrencyOfItsOrigin() {
        arrange(2080);
        publishDefaults(2080);
        Invoice origin = installmentDueOn(LocalDate.of(2080, 3, 12), Money.of(new BigDecimal("100.00"), Currency.USD));
        BillingInvariantChecker invariants = BillingInvariantChecker.startingNow(jdbc);

        reconcile(origin, LocalDate.of(2080, 3, 18));

        Map<String, Object> charge = chargesOf(origin).get(0);
        assertThat(charge.get("currency")).isEqualTo("USD");
        assertThat(jdbc.queryForObject("select currency from invoice where id = ?", String.class, charge.get("debit_note_id"))).isEqualTo("USD");
        assertThat(charge.get("nd_total")).isEqualTo(new BigDecimal("5.00"));
        invariants.assertAllBillingInvariantsHold();
    }

    @Test
    @Tag("D-140")
    @Tag("D-089")
    void unallocatedCreditNeverPaysTheDebitNoteBeforeItsOrigin() {
        arrange(2077);
        publishDefaults(2077);
        Invoice origin = installmentDueOn(LocalDate.of(2077, 3, 12), ars(80000));
        Long accountId = reload(origin).getStudentCurrentAccountId();
        transactionTemplate.executeWithoutResult(status -> accountService.recordPaymentReceivedCredit(accountService.lockStudentCurrentAccount(accountId),
                System.nanoTime(), NonNegativeMoney.of(ars(5000)), LocalDate.of(2077, 3, 17), "Pago"));
        BillingInvariantChecker invariants = BillingInvariantChecker.startingNow(jdbc);

        reconcile(origin, LocalDate.of(2077, 3, 18));

        Map<String, Object> charge = chargesOf(origin).get(0);
        assertThat(jdbc.queryForObject("select payment_status from invoice where id = ?", String.class, charge.get("debit_note_id"))).isNotEqualTo("PAID");
        assertThat(jdbc.queryForObject("select payment_status from invoice where id = ?", String.class, origin.getId())).isEqualTo("OVERDUE");
        assertThat(jdbc.queryForObject("select unallocated_credit_amount from student_current_account where id = ?", BigDecimal.class, accountId)).isEqualByComparingTo("5000.00");
        invariants.assertAllBillingInvariantsHold();
    }

    @Test
    @Tag("D-061")
    void whenThePriorCheckMissesAnExistingChargeTheDatabaseUniquenessIsTheBarrierAndItIsTranslatedToAlreadyIssued() {
        arrange(2078);
        publishDefaults(2078);
        Invoice origin = installmentDueOn(LocalDate.of(2078, 3, 12), ars(80000));
        LocalDate day = LocalDate.of(2078, 3, 18);
        assertThat(accrualService.reconcileLateFeesOfInvoice(origin.getId(), day)).isEqualTo(1);
        Mockito.doReturn(java.util.Set.of()).when(chargeRepositorySpy).findMonthOrdinalsByOriginInvoiceId(origin.getId());

        int issuedByTheLoser = accrualService.reconcileLateFeesOfInvoice(origin.getId(), day);

        assertThat(issuedByTheLoser).isZero();
        assertThat(chargesOf(origin)).hasSize(1);
        assertThat(jdbc.queryForObject("select count(*) from invoice where origin_invoice_id = ?", Long.class, origin.getId())).isEqualTo(1L);
    }

    @Test
    @Tag("D-239")
    @Tag("D-149")
    @Tag("D-294")
    void theLateFeeInvariantCheckerDetectsADebitNoteOverADebitNoteAndAChargeBeforeEnablement() {
        arrange(2079);
        publishDefaults(2079);
        Invoice origin = installmentDueOn(LocalDate.of(2079, 3, 12), ars(80000));
        reconcile(origin, LocalDate.of(2079, 3, 18));
        BillingInvariantChecker clean = BillingInvariantChecker.startingNow(jdbc);
        Invoice firstSpare = installmentDueOn(LocalDate.of(2079, 4, 12), ars(80000));
        Invoice secondSpare = installmentDueOn(LocalDate.of(2079, 5, 12), ars(80000));
        BillingInvariantChecker invariants = BillingInvariantChecker.startingNow(jdbc);
        Map<String, Object> charge = chargesOf(origin).get(0);
        Long accountId = reload(origin).getStudentCurrentAccountId();
        Long versionId = (Long) charge.get("late_payment_version_id");
        Long debitNoteId = (Long) charge.get("debit_note_id");
        String insert = "insert into late_fee_charge (debit_note_id, origin_invoice_id, student_current_account_id, month_ordinal, fact_date, late_payment_version_id, monthly_rate, base_amount, currency, created_at) "
                + "values (?, ?, ?, ?, ?, ?, 0.05, 100, 'ARS', now())";
        try {
            jdbc.update(insert, firstSpare.getId(), debitNoteId, accountId, 1, java.sql.Date.valueOf("2079-04-18"), versionId);
            jdbc.update(insert, secondSpare.getId(), origin.getId(), accountId, 2, java.sql.Date.valueOf("2000-01-01"), versionId);

            List<String> violations = clean.lateFeesRespectTheRedefinedI11();

            assertThat(violations).anyMatch(v -> v.startsWith("I-11 late fee over a debit note") && v.contains("debit note " + firstSpare.getId()));
            assertThat(violations).anyMatch(v -> v.startsWith("I-11 late fee dated before charging was enabled") && v.contains("debit note " + secondSpare.getId()));
            assertThat(violations).noneMatch(v -> v.contains("debit note " + debitNoteId));
        } finally {
            jdbc.update("delete from late_fee_charge where debit_note_id in (?, ?)", firstSpare.getId(), secondSpare.getId());
        }
        invariants.assertAllBillingInvariantsHold();
    }
}
