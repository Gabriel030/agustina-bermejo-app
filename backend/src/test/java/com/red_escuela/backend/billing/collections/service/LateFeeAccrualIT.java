package com.red_escuela.backend.billing.collections.service;

import com.red_escuela.backend.academicstructure.entity.Course;
import com.red_escuela.backend.academicstructure.entity.Institution;
import com.red_escuela.backend.academicstructure.entity.Program;
import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.academicstructure.repository.ProgramRepository;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentStatus;
import com.red_escuela.backend.enrollment.repository.ProgramEnrollmentRepository;
import com.red_escuela.backend.academicstructure.repository.CourseRepository;
import com.red_escuela.backend.academicstructure.repository.InstitutionRepository;
import com.red_escuela.backend.academicstructure.repository.TermRepository;
import com.red_escuela.backend.billing.account.service.StudentCurrentAccountService;
import com.red_escuela.backend.billing.collections.operation.OverdueInvoiceDetectionStep;
import com.red_escuela.backend.billing.collections.repository.LateFeeChargeRepository;
import com.red_escuela.backend.billing.configuration.repository.ConfigurationVersionRepository;
import com.red_escuela.backend.billing.events.LateFeeCutoffKind;
import com.red_escuela.backend.billing.events.LateFeeIssuedEvent;
import com.red_escuela.backend.billing.invariant.BillingInvariantChecker;
import com.red_escuela.backend.billing.invoice.domain.FeeType;
import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.billing.invoice.service.PendingInvoiceQueryService;
import com.red_escuela.backend.billing.service.InvoiceOverdueSweepService;
import com.red_escuela.backend.billing.invoice.domain.event.InvoiceMarkedAsOverdueEvent;
import org.springframework.context.ApplicationEventPublisher;
import com.red_escuela.backend.billing.invoice.domain.InvoicePaymentStatus;
import com.red_escuela.backend.billing.invoice.repository.InvoiceRepository;
import com.red_escuela.backend.billing.service.LatePaymentTestFixtures;
import com.red_escuela.backend.billing.support.BillingTestFixtures;
import com.red_escuela.backend.billing.support.PriceFixtures;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

@SpringBootTest
@ActiveProfiles("test")
@RecordApplicationEvents
class LateFeeAccrualIT extends AbstractIntegrationTest {
    private static final StatusChangeActor ACTOR = new StatusChangeActor(null, "LateFeeAccrualIT");

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
    @Autowired
    private InvoiceOverdueSweepService sweepService;
    @MockitoSpyBean
    private PendingInvoiceQueryService pendingInvoiceQueryService;
    @MockitoSpyBean
    private LateFeeChargeRepository chargeRepositorySpy;

    @Autowired
    private ApplicationEventPublisher eventPublisher;
    @Autowired
    private ProgramRepository programRepository;
    @Autowired
    private ProgramEnrollmentRepository programEnrollmentRepository;

    private Person person;
    private Student student;
    private Term term;
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
        student = new Student();
        student.setPerson(person);
        student.setInstitution(institution);
        student.setOnboardingStatus(StudentOnboardingStatus.CONFIRMADO);
        student = studentRepository.save(student);
        term = new Term();
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
        publish(0, "0.002", 10, true, true, LocalDate.of(year, 1, 1));
    }

    private ProgramEnrollment programEnrollment() {
        Program program = new Program();
        program.setName("Carrera recargo " + System.nanoTime());
        program.setTotalYears(1);
        program.setTotalCreditsRequired(0);
        program = programRepository.save(program);
        ProgramEnrollment created = new ProgramEnrollment();
        created.setStudent(student);
        created.setProgram(program);
        created.setStatus(ProgramEnrollmentStatus.ENROLLED);
        created.setEnrollmentDate(LocalDateTime.now());
        return programEnrollmentRepository.save(created);
    }

    private Invoice programInstallmentDueOn(ProgramEnrollment programEnrollment, LocalDate dueDate) {
        return BillingTestFixtures.invoice().forProgramEnrollment(programEnrollment).forPeriod(YearMonth.from(dueDate)).dueOn(dueDate).withBaseAmount(ars(100000)).overdue().build();
    }

    private List<String> factDatesOf(Invoice origin) {
        return chargesOf(origin).stream().map(c -> c.get("fact_date").toString()).toList();
    }

    private List<String> summaryOf(Invoice origin) {
        return chargesOf(origin).stream().map(c -> c.get("period_start") + ".." + c.get("period_end") + ":" + c.get("charged_days") + ":" + c.get("nd_total")).toList();
    }

    private void reconcileEveryDay(Invoice origin, LocalDate from, LocalDate to) {
        for (LocalDate day = from; !day.isAfter(to); day = day.plusDays(1)) {
            reconcile(origin, day);
        }
    }

    private static LocalDate d(int year, int month, int day) {
        return LocalDate.of(year, month, day);
    }

    private Invoice installmentDueOn(LocalDate dueDate, Money amount) {
        return BillingTestFixtures.invoice().forCourseEnrollment(enrollment).forPeriod(YearMonth.from(dueDate)).dueOn(dueDate).withBaseAmount(amount).overdue().build();
    }

    private Invoice pendingInstallmentDueOn(LocalDate dueDate) {
        return BillingTestFixtures.invoice().forCourseEnrollment(enrollment).forPeriod(YearMonth.from(dueDate)).dueOn(dueDate).withBaseAmount(ars(80000)).build();
    }

    private List<Map<String, Object>> chargesOf(Invoice origin) {
        return jdbc.queryForList("select c.*, d.total as nd_total, d.due_date as nd_due, d.issue_date as nd_issue, d.invoice_number as nd_number, d.document_type, d.invoice_type, d.origin_invoice_id "
                + "from late_fee_charge c join invoice d on d.id = c.debit_note_id where c.origin_invoice_id = ? order by c.period_start", origin.getId());
    }

    private void reconcile(Invoice origin, LocalDate date) {
        accrualService.reconcileLateFeesOfInvoice(origin.getId(), date);
    }

    private Invoice reload(Invoice invoice) {
        return invoiceRepository.findInvoiceWithLinesById(invoice.getId()).orElseThrow();
    }

    private Invoice pendingInstallment(LocalDate dueDate, Money amount) {
        return BillingTestFixtures.invoice().forCourseEnrollment(enrollment).forPeriod(YearMonth.from(dueDate)).dueOn(dueDate).withBaseAmount(amount).build();
    }

    @Test
    @Tag("D-332")
    @Tag("D-330")
    @Tag("D-060")
    @Tag("D-061")
    @Tag("D-149")
    @Tag("D-244")
    void monthCloseIssuesOneDebitNotePerMonthForUnpaidInstallment() {
        arrange(1950);
        publish(0, "0.002", 10, true, true, d(1950, 1, 1));
        Invoice origin = pendingInstallment(d(1950, 3, 11), ars(100000));
        Mockito.doAnswer(call -> (Long) call.getArgument(0) < origin.getId() ? List.of(origin.getId()) : List.<Long>of())
                .when(pendingInvoiceQueryService).findOverdueLateFeeOriginIdsNotCoveredUpToAfter(Mockito.anyLong(), Mockito.any(LocalDate.class), Mockito.anyInt());
        BillingInvariantChecker invariants = BillingInvariantChecker.startingNow(jdbc);

        for (LocalDate day = d(1950, 3, 11); !day.isAfter(d(1950, 5, 2)); day = day.plusDays(1)) {
            sweepService.run(day);
            int expected = day.isBefore(d(1950, 4, 1)) ? 0 : day.isBefore(d(1950, 5, 1)) ? 1 : 2;
            assertThat(chargesOf(origin)).as("charges after the sweep of %s", day).hasSize(expected);
        }

        List<Map<String, Object>> charges = chargesOf(origin);
        Map<String, Object> march = charges.get(0);
        assertThat(march.get("period_start").toString()).isEqualTo("1950-03-12");
        assertThat(march.get("period_end").toString()).isEqualTo("1950-03-31");
        assertThat(march.get("charged_days")).isEqualTo(20);
        assertThat(march.get("nd_total")).isEqualTo(new BigDecimal("4000.00"));
        assertThat(march.get("fact_date").toString()).isEqualTo("1950-03-31");
        assertThat(march.get("nd_issue").toString()).isEqualTo("1950-04-01");
        assertThat(march.get("nd_due").toString()).isEqualTo("1950-04-11");
        assertThat(march.get("cutoff_kind")).isEqualTo("MONTH_CLOSE");
        assertThat(march.get("settlement_allocation_id")).isNull();
        assertThat(march.get("document_type")).isEqualTo("DEBIT_NOTE");
        assertThat(march.get("invoice_type")).isEqualTo("LATE_FEE");
        assertThat((String) march.get("nd_number")).startsWith("ND-");
        assertThat(march.get("origin_invoice_id")).isEqualTo(origin.getId());
        assertThat((BigDecimal) march.get("daily_rate")).isEqualByComparingTo("0.002");
        assertThat((BigDecimal) march.get("base_amount")).isEqualByComparingTo("100000.00");
        assertThat(summaryOf(origin)).containsExactly("1950-03-12..1950-03-31:20:4000.00", "1950-04-01..1950-04-30:30:6000.00");
        assertThat(jdbc.queryForList("select charge_key from invoice_line where charge_key like ? order by charge_key", String.class, "LF:" + origin.getId() + ":D%"))
                .containsExactly("LF:" + origin.getId() + ":D1950-03-12", "LF:" + origin.getId() + ":D1950-04-01");
        assertThat(reload(origin).getPaymentStatus()).isEqualTo(InvoicePaymentStatus.OVERDUE);
        assertThat(reload(origin).getAmounts().total().value().amount()).isEqualByComparingTo("100000.00");
        assertThat(jdbc.queryForObject("select student_snapshot::text from invoice where id = ?", String.class, origin.getId()))
                .isEqualTo(jdbc.queryForObject("select student_snapshot::text from invoice where id = ?", String.class, march.get("debit_note_id")));
        invariants.assertAllBillingInvariantsHold();
    }

    @Test
    @Tag("D-331")
    @Tag("D-332")
    void installmentStillInGraceAtMonthCloseIsChargedWhenItBecomesOverdue() {
        arrange(2101);
        publish(10, "0.002", 10, true, true, d(2101, 1, 1));
        Invoice origin = pendingInstallment(d(2101, 3, 25), ars(100000));

        transitionService.markAsOverdueWhenPastDueDate(origin.getId(), d(2101, 4, 1));
        reconcile(origin, d(2101, 4, 1));
        assertThat(reload(origin).getPaymentStatus()).isEqualTo(InvoicePaymentStatus.PENDING);
        assertThat(chargesOf(origin)).isEmpty();

        transitionService.markAsOverdueWhenPastDueDate(origin.getId(), d(2101, 4, 5));
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> assertThat(summaryOf(origin)).containsExactly("2101-03-26..2101-03-31:6:1200.00"));
        Map<String, Object> march = chargesOf(origin).get(0);
        assertThat(march.get("fact_date").toString()).isEqualTo("2101-03-31");
        assertThat(march.get("nd_issue").toString()).isEqualTo("2101-04-05");

        reconcile(origin, d(2101, 5, 1));
        assertThat(summaryOf(origin)).containsExactly("2101-03-26..2101-03-31:6:1200.00", "2101-04-01..2101-04-30:30:6000.00");
        BillingInvariantChecker.startingNow(jdbc).assertAllBillingInvariantsHold();
    }

    @Test
    @Tag("D-288")
    @Tag("D-182")
    void allDebitNotesOfAnInstallmentUseTheRateAndGraceOfItsDueDateVersion() {
        arrange(2102);
        publish(0, "0.002", 10, true, true, d(2102, 1, 1));
        publish(5, "0.003", 15, true, true, d(2102, 4, 1));
        Invoice march = installmentDueOn(d(2102, 3, 11), ars(100000));
        Invoice april = installmentDueOn(d(2102, 4, 11), ars(100000));

        reconcile(march, d(2102, 4, 1));
        reconcile(april, d(2102, 4, 1));
        reconcile(march, d(2102, 5, 1));
        reconcile(april, d(2102, 5, 1));

        assertThat(summaryOf(march)).containsExactly("2102-03-12..2102-03-31:20:4000.00", "2102-04-01..2102-04-30:30:6000.00");
        assertThat(summaryOf(april)).containsExactly("2102-04-12..2102-04-30:19:5700.00");
        assertThat(chargesOf(april).get(0).get("nd_total")).isEqualTo(new BigDecimal("5700.00"));
        assertThat(java.time.temporal.ChronoUnit.DAYS.between(LocalDate.parse(chargesOf(april).get(0).get("nd_issue").toString()),
                LocalDate.parse(chargesOf(april).get(0).get("nd_due").toString()))).isEqualTo(15L);
        assertThat(java.time.temporal.ChronoUnit.DAYS.between(LocalDate.parse(chargesOf(march).get(1).get("nd_issue").toString()),
                LocalDate.parse(chargesOf(march).get(1).get("nd_due").toString()))).isEqualTo(10L);
    }

    @Test
    @Tag("D-294")
    @Tag("D-210")
    void chargingEnablementCountsOnlyEnabledDays() {
        arrange(2103);
        publish(0, "0.002", 10, true, false, d(2103, 1, 1));
        publish(0, "0.002", 10, true, true, d(2103, 3, 20));
        publish(0, "0.002", 10, true, false, d(2103, 4, 15));
        Invoice origin = installmentDueOn(d(2103, 3, 11), ars(100000));

        reconcile(origin, d(2103, 4, 1));
        reconcile(origin, d(2103, 5, 1));

        assertThat(summaryOf(origin)).containsExactly("2103-03-12..2103-03-31:12:2400.00", "2103-04-01..2103-04-30:14:2800.00");
        BillingInvariantChecker.startingNow(jdbc).assertAllBillingInvariantsHold();
    }

    @Test
    @Tag("D-210")
    @Tag("D-294")
    void disabledChargingIssuesNothingAndNeverBackfills() {
        arrange(2104);
        publish(0, "0.002", 10, true, false, d(2104, 1, 1));
        publish(0, "0.002", 10, true, true, d(2104, 6, 1));
        Invoice origin = installmentDueOn(d(2104, 3, 11), ars(100000));
        BillingInvariantChecker invariants = BillingInvariantChecker.startingNow(jdbc);

        reconcile(origin, d(2104, 4, 1));
        reconcile(origin, d(2104, 5, 1));
        reconcile(origin, d(2104, 6, 1));
        assertThat(chargesOf(origin)).isEmpty();
        assertThat(jdbc.queryForObject("select count(*) from invoice where origin_invoice_id = ?", Long.class, origin.getId())).isZero();
        reconcile(origin, d(2104, 7, 2));

        assertThat(summaryOf(origin)).containsExactly("2104-06-01..2104-06-30:30:6000.00");
        invariants.assertAllBillingInvariantsHold();
    }

    private void withdrawCourseEnrollmentOn(LocalDate date) {
        enrollment.setStatus(EnrollmentStatus.WITHDRAWN);
        enrollment.setCompletionDate(date.atTime(10, 0));
        courseEnrollmentRepository.save(enrollment);
    }

    @Test
    @Tag("D-148")
    @Tag("D-293")
    void aVoluntaryWithdrawalCutsTheLateFeeAtTheEndDayAndNothingFollows() {
        arrange(2105);
        publishDefaults(2105);
        Invoice origin = installmentDueOn(d(2105, 3, 11), ars(100000));
        withdrawCourseEnrollmentOn(d(2105, 3, 20));

        reconcile(origin, d(2105, 4, 1));
        reconcile(origin, d(2105, 5, 1));
        reconcile(origin, d(2105, 6, 1));

        assertThat(summaryOf(origin)).containsExactly("2105-03-12..2105-03-20:9:1800.00");
        assertThat(reload(origin).getPaymentStatus()).isEqualTo(InvoicePaymentStatus.OVERDUE);
        BillingInvariantChecker.startingNow(jdbc).assertAllBillingInvariantsHold();
    }

    @Test
    @Tag("D-148")
    @Tag("D-293")
    void withFreezingDisabledTheLateFeesKeepRunningAfterTheEnd() {
        arrange(2106);
        publish(0, "0.002", 10, false, true, d(2106, 1, 1));
        Invoice origin = installmentDueOn(d(2106, 3, 11), ars(100000));
        withdrawCourseEnrollmentOn(d(2106, 3, 20));

        reconcile(origin, d(2106, 6, 1));

        assertThat(summaryOf(origin)).containsExactly("2106-03-12..2106-03-31:20:4000.00", "2106-04-01..2106-04-30:30:6000.00", "2106-05-01..2106-05-31:31:6200.00");
    }

    @Test
    @Tag("D-325")
    @Tag("D-244")
    @Tag("D-332")
    void skippedRunsAreRecoveredWithRealFactDatesAndSecretariatNotice() {
        arrange(2107);
        publishDefaults(2107);
        Invoice origin = installmentDueOn(d(2107, 3, 11), ars(100000));

        reconcile(origin, d(2107, 5, 21));

        assertThat(summaryOf(origin)).containsExactly("2107-03-12..2107-03-31:20:4000.00", "2107-04-01..2107-04-30:30:6000.00");
        List<Map<String, Object>> charges = chargesOf(origin);
        assertThat(charges.get(0).get("fact_date").toString()).isEqualTo("2107-03-31");
        assertThat(charges.get(1).get("fact_date").toString()).isEqualTo("2107-04-30");
        assertThat(charges).allSatisfy(c -> {
            assertThat(c.get("nd_issue").toString()).isEqualTo("2107-05-21");
            assertThat(c.get("nd_due").toString()).isEqualTo("2107-05-31");
        });
        List<LateFeeIssuedEvent> events = applicationEvents.stream(LateFeeIssuedEvent.class).filter(e -> e.originInvoiceId().equals(origin.getId())).toList();
        assertThat(events).hasSize(2);
        assertThat(events).extracting(LateFeeIssuedEvent::periodStart).containsExactly(d(2107, 3, 12), d(2107, 4, 1));
        assertThat(events).extracting(LateFeeIssuedEvent::periodEnd).containsExactly(d(2107, 3, 31), d(2107, 4, 30));
        assertThat(events).extracting(LateFeeIssuedEvent::chargedDays).containsExactly(20, 30);
        assertThat(events).extracting(LateFeeIssuedEvent::cutoffKind).containsOnly(LateFeeCutoffKind.MONTH_CLOSE);
        assertThat(events).extracting(LateFeeIssuedEvent::issuedAfterFiscalWindow).containsExactly(true, true);
    }

    @Test
    @Tag("D-297")
    @Tag("D-151")
    void lateFeeBaseIsTheLinePriceBeforeAnyScholarshipAndTheChargeKeepsThePhoto() {
        arrange(2108);
        publishDefaults(2108);
        Invoice origin = BillingTestFixtures.invoice().forCourseEnrollment(enrollment).forPeriod(YearMonth.of(2108, 3)).dueOn(d(2108, 3, 11))
                .withBaseAmount(ars(100000)).withScholarshipOf(ars(50000)).overdue().build();

        reconcile(origin, d(2108, 4, 1));

        Map<String, Object> charge = chargesOf(origin).get(0);
        assertThat(charge.get("nd_total")).isEqualTo(new BigDecimal("4000.00"));
        assertThat((BigDecimal) charge.get("base_amount")).isEqualByComparingTo("100000.00");
        assertThat(charge.get("late_payment_version_id")).isNotNull();
        Invoice reloaded = reload(origin);
        assertThat(reloaded.getPrimaryLine().getAmount().value().amount()).isEqualByComparingTo("100000.00");
        assertThat(reloaded.getPrimaryLine().calculateNetAmount().value().amount()).isEqualByComparingTo("50000.00");
    }

    @Test
    @Tag("D-297")
    @Tag("D-240")
    void lateFeeBaseIsTheFinalPriceAfterTheMarketingDiscountAndNotTheListPrice() {
        arrange(2109);
        publishDefaults(2109);
        Invoice origin = BillingTestFixtures.invoice().forCourseEnrollment(enrollment).forPeriod(YearMonth.of(2109, 3)).dueOn(d(2109, 3, 11))
                .withBaseAmount(ars(100000)).withMarketingDiscountOf(ars(100000)).overdue().build();

        reconcile(origin, d(2109, 4, 1));

        Map<String, Object> charge = chargesOf(origin).get(0);
        assertThat((BigDecimal) charge.get("base_amount")).isEqualByComparingTo("100000.00");
        assertThat((BigDecimal) charge.get("nd_total")).isEqualByComparingTo("4000.00");
        assertThat(jdbc.queryForObject("select list_amount from invoice_line where invoice_id = ?", BigDecimal.class, origin.getId())).isEqualByComparingTo("200000.00");
    }

    @Test
    @Tag("D-089")
    @Tag("D-149")
    @Tag("D-289")
    @Tag("D-155")
    void debitNotesAndNonAccruingChargesNeverAccrue() {
        arrange(2110);
        publishDefaults(2110);
        LocalDate dueDate = d(2110, 3, 11);
        Invoice installment = installmentDueOn(dueDate, ars(100000));
        Invoice enrollmentFee = BillingTestFixtures.invoice().forCourseEnrollment(enrollment).ofType(FeeType.ENROLLMENT_FEE).forPeriod(YearMonth.from(dueDate)).dueOn(dueDate)
                .withBaseAmount(ars(50000)).overdue().build();
        Invoice examFee = BillingTestFixtures.invoice().forCourseEnrollment(enrollment).ofType(FeeType.EXAM_FEE).forPeriod(YearMonth.from(dueDate)).dueOn(dueDate)
                .withBaseAmount(ars(20000)).overdue().build();
        reconcile(installment, d(2110, 4, 1));
        Long debitNoteId = (Long) chargesOf(installment).get(0).get("debit_note_id");

        for (LocalDate day = d(2110, 4, 12); !day.isAfter(d(2110, 7, 1)); day = day.plusDays(1)) {
            transitionService.markAsOverdueWhenPastDueDate(debitNoteId, day);
            reconcile(invoiceRepository.findInvoiceWithLinesById(debitNoteId).orElseThrow(), day);
        }
        for (Invoice origin : List.of(enrollmentFee, examFee)) {
            reconcile(origin, d(2110, 4, 1));
        }

        assertThat(invoiceRepository.findById(debitNoteId).orElseThrow().getPaymentStatus()).isEqualTo(InvoicePaymentStatus.OVERDUE);
        assertThat(jdbc.queryForObject("select count(*) from late_fee_charge where origin_invoice_id = ?", Long.class, debitNoteId)).isZero();
        assertThat(summaryOf(enrollmentFee)).containsExactly("2110-03-12..2110-03-31:20:2000.00");
        assertThat(chargesOf(examFee)).isEmpty();
        assertThat(invoiceRepository.findOverdueLateFeeOriginIdsNotCoveredUpToAfter(0L, d(2110, 3, 31), 100000)).doesNotContain(examFee.getId());
    }

    @Test
    @Tag("D-153")
    @Tag("D-154")
    @Tag("D-208")
    void paymentReviewSkipsMonthCloseAndRejectionCatchesUp() {
        arrange(2111);
        publishDefaults(2111);
        LocalDate dueDate = d(2111, 3, 11);
        LocalDate releaseDate = d(2111, 4, 20);
        Invoice underReview = BillingTestFixtures.invoice().forCourseEnrollment(enrollment).forPeriod(YearMonth.from(dueDate)).dueOn(dueDate).withBaseAmount(ars(100000))
                .customizedBy(created -> created.markPaymentUnderReview(dueDate.minusDays(1), ACTOR, "Comprobante a tiempo")).build();
        Invoice rejected = BillingTestFixtures.invoice().forCourseEnrollment(enrollment).forPeriod(YearMonth.from(dueDate)).dueOn(dueDate).withBaseAmount(ars(100000))
                .customizedBy(created -> {
                    created.markPaymentUnderReview(dueDate.minusDays(1), ACTOR, "Comprobante a tiempo");
                    created.releasePaymentReview(releaseDate, 5, ACTOR, "Comprobante rechazado");
                }).build();
        BillingInvariantChecker invariants = BillingInvariantChecker.startingNow(jdbc);

        reconcile(underReview, d(2111, 4, 1));
        reconcile(underReview, d(2111, 5, 1));
        assertThat(chargesOf(underReview)).isEmpty();
        assertThat(reload(underReview).getPaymentStatus()).isEqualTo(InvoicePaymentStatus.PAYMENT_UNDER_REVIEW);

        assertThat(reload(rejected).getPaymentStatus()).isEqualTo(InvoicePaymentStatus.OVERDUE);
        assertThat(chargesOf(rejected)).isEmpty();
        transactionTemplate.executeWithoutResult(status -> eventPublisher.publishEvent(new InvoiceMarkedAsOverdueEvent(rejected.getId(), dueDate, releaseDate)));
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> assertThat(summaryOf(rejected)).containsExactly("2111-03-12..2111-03-31:20:4000.00"));
        reconcile(rejected, d(2111, 5, 1));
        assertThat(summaryOf(rejected)).containsExactly("2111-03-12..2111-03-31:20:4000.00", "2111-04-01..2111-04-30:30:6000.00");
        invariants.assertAllBillingInvariantsHold();
    }

    @Test
    @Tag("D-332")
    void rerunsAndListenerOnTheSameDayIssueOneDebitNotePerPeriod() {
        arrange(2112);
        publishDefaults(2112);
        Invoice origin = installmentDueOn(d(2112, 3, 11), ars(100000));

        reconcile(origin, d(2112, 4, 1));
        reconcile(origin, d(2112, 4, 1));
        detectionStep.detectOverdueInvoices(d(2112, 4, 1));
        transactionTemplate.executeWithoutResult(status -> eventPublisher.publishEvent(new InvoiceMarkedAsOverdueEvent(origin.getId(), d(2112, 3, 11), d(2112, 4, 1))));

        await().pollDelay(Duration.ofSeconds(2)).atMost(Duration.ofSeconds(20)).untilAsserted(() -> assertThat(summaryOf(origin)).containsExactly("2112-03-12..2112-03-31:20:4000.00"));
    }

    @Test
    @Tag("D-332")
    void parallelRunsIssueASingleDebitNotePerPeriodAndNobodyFails() throws Exception {
        arrange(2113);
        publishDefaults(2113);
        Invoice origin = installmentDueOn(d(2113, 3, 11), ars(100000));
        ExecutorService executor = Executors.newFixedThreadPool(6);
        try {
            List<Callable<Integer>> tasks = java.util.stream.IntStream.range(0, 6)
                    .<Callable<Integer>>mapToObj(i -> () -> accrualService.reconcileLateFeesOfInvoice(origin.getId(), d(2113, 4, 1))).toList();
            int issued = 0;
            for (Future<Integer> future : executor.invokeAll(tasks)) {
                issued += future.get();
            }
            assertThat(issued).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
        assertThat(summaryOf(origin)).containsExactly("2113-03-12..2113-03-31:20:4000.00");
    }

    @Test
    @Tag("D-332")
    void whenThePriorCheckMissesAnExistingChargeTheDatabaseIsTheBarrierAndItIsTranslatedToAlreadyIssued() {
        arrange(2114);
        publishDefaults(2114);
        Invoice origin = installmentDueOn(d(2114, 3, 11), ars(100000));
        LocalDate day = d(2114, 4, 1);
        assertThat(accrualService.reconcileLateFeesOfInvoice(origin.getId(), day)).isEqualTo(1);
        Mockito.doReturn(List.of()).when(chargeRepositorySpy).findCoveredPeriodsByOriginInvoiceId(origin.getId());

        int issuedByTheLoser = accrualService.reconcileLateFeesOfInvoice(origin.getId(), day);

        assertThat(issuedByTheLoser).isZero();
        assertThat(summaryOf(origin)).containsExactly("2114-03-12..2114-03-31:20:4000.00");
        assertThat(jdbc.queryForObject("select count(*) from invoice where origin_invoice_id = ?", Long.class, origin.getId())).isEqualTo(1L);
    }

    @Test
    @Tag("D-332")
    void databaseRejectsOverlappingAndMonthCrossingPeriods() {
        arrange(2115);
        publishDefaults(2115);
        Invoice origin = installmentDueOn(d(2115, 3, 11), ars(100000));
        reconcile(origin, d(2115, 4, 1));
        BillingInvariantChecker clean = BillingInvariantChecker.startingNow(jdbc);
        Invoice overlapping = installmentDueOn(d(2115, 4, 12), ars(80000));
        Invoice sameStart = installmentDueOn(d(2115, 5, 12), ars(80000));
        Invoice crossing = installmentDueOn(d(2115, 6, 12), ars(80000));
        Map<String, Object> charge = chargesOf(origin).get(0);
        Long accountId = reload(origin).getStudentCurrentAccountId();
        Long versionId = (Long) charge.get("late_payment_version_id");

        assertThatThrownBy(() -> insertRawCharge(overlapping.getId(), origin.getId(), accountId, d(2115, 3, 20), d(2115, 3, 31), 12, versionId))
                .hasMessageContaining("ex_late_fee_charge_no_overlapping_days");
        assertThatThrownBy(() -> insertRawCharge(sameStart.getId(), origin.getId(), accountId, d(2115, 3, 12), d(2115, 3, 20), 9, versionId))
                .satisfiesAnyOf(e -> assertThat(e.getMessage()).contains("uk_late_fee_charge_origin_period_start"), e -> assertThat(e.getMessage()).contains("ex_late_fee_charge_no_overlapping_days"));
        assertThatThrownBy(() -> insertRawCharge(crossing.getId(), origin.getId(), accountId, d(2115, 3, 25), d(2115, 4, 2), 9, versionId))
                .hasMessageContaining("ck_late_fee_charge_period_one_month");
        assertThat(jdbc.queryForObject("select count(*) from late_fee_charge where origin_invoice_id = ?", Long.class, origin.getId())).isEqualTo(1L);
        assertThat(clean.lateFeesRespectTheRedefinedI11()).isEmpty();
    }

    private void insertRawCharge(Long debitNoteId, Long originId, Long accountId, LocalDate start, LocalDate end, int chargedDays, Long versionId) {
        jdbc.update("insert into late_fee_charge (debit_note_id, origin_invoice_id, student_current_account_id, period_start, period_end, charged_days, cutoff_kind, fact_date, "
                + "late_payment_version_id, daily_rate, base_amount, currency, created_at) values (?, ?, ?, ?, ?, ?, 'MONTH_CLOSE', ?, ?, 0.002, 100, 'ARS', now())",
                debitNoteId, originId, accountId, java.sql.Date.valueOf(start), java.sql.Date.valueOf(end), chargedDays, java.sql.Date.valueOf(end), versionId);
    }

    @Test
    @Tag("D-332")
    void yearBoundaryClosesDecemberAndKeepsAccruing() {
        arrange(2127);
        publishDefaults(2127);
        Invoice origin = programInstallmentDueOn(programEnrollment(), d(2127, 12, 10));

        reconcile(origin, d(2128, 3, 2));

        assertThat(summaryOf(origin)).containsExactly("2127-12-11..2127-12-31:21:4200.00", "2128-01-01..2128-01-31:31:6200.00", "2128-02-01..2128-02-29:29:5800.00");
        assertThat(jdbc.queryForList("select charge_key from invoice_line where charge_key like ? order by charge_key", String.class, "LF:" + origin.getId() + ":D%")).hasSize(3);
    }

    @Test
    @Tag("D-097")
    void theDebitNoteFollowsTheCurrencyOfItsOrigin() {
        arrange(2116);
        publishDefaults(2116);
        Invoice origin = installmentDueOn(d(2116, 3, 11), Money.of(new BigDecimal("100.00"), Currency.USD));
        BillingInvariantChecker invariants = BillingInvariantChecker.startingNow(jdbc);

        reconcile(origin, d(2116, 4, 1));

        Map<String, Object> charge = chargesOf(origin).get(0);
        assertThat(charge.get("currency")).isEqualTo("USD");
        assertThat(jdbc.queryForObject("select currency from invoice where id = ?", String.class, charge.get("debit_note_id"))).isEqualTo("USD");
        assertThat(charge.get("nd_total")).isEqualTo(new BigDecimal("4.00"));
        invariants.assertAllBillingInvariantsHold();
    }

    @Test
    @Tag("D-140")
    @Tag("D-089")
    void unallocatedCreditNeverPaysTheDebitNoteBeforeItsOrigin() {
        arrange(2117);
        publishDefaults(2117);
        Invoice origin = installmentDueOn(d(2117, 3, 11), ars(100000));
        Long accountId = reload(origin).getStudentCurrentAccountId();
        transactionTemplate.executeWithoutResult(status -> accountService.recordPaymentReceivedCredit(accountService.lockStudentCurrentAccount(accountId),
                System.nanoTime(), NonNegativeMoney.of(ars(5000)), d(2117, 3, 17), "Pago"));
        BillingInvariantChecker invariants = BillingInvariantChecker.startingNow(jdbc);

        reconcile(origin, d(2117, 4, 1));

        Map<String, Object> charge = chargesOf(origin).get(0);
        assertThat(charge.get("nd_total")).isEqualTo(new BigDecimal("4000.00"));
        assertThat(jdbc.queryForObject("select payment_status from invoice where id = ?", String.class, charge.get("debit_note_id"))).isNotEqualTo("PAID");
        assertThat(jdbc.queryForObject("select payment_status from invoice where id = ?", String.class, origin.getId())).isEqualTo("OVERDUE");
        assertThat(jdbc.queryForObject("select unallocated_credit_amount from student_current_account where id = ?", BigDecimal.class, accountId)).isEqualByComparingTo("5000.00");
        invariants.assertAllBillingInvariantsHold();
    }

    private void dropConstraint(String name) {
        jdbc.execute("alter table late_fee_charge drop constraint " + name);
    }

    @Test
    @Tag("D-330")
    @Tag("D-332")
    @Tag("D-293")
    @Tag("D-294")
    @Tag("D-149")
    void invariantCheckerDetectsEachDailyI11Violation() {
        arrange(2118);
        publishDefaults(2118);
        Invoice origin = installmentDueOn(d(2118, 3, 11), ars(100000));
        reconcile(origin, d(2118, 4, 1));
        BillingInvariantChecker.startingNow(jdbc).assertAllBillingInvariantsHold();
        publish(0, "0.002", 10, true, false, d(2118, 6, 1));
        BillingInvariantChecker clean = BillingInvariantChecker.startingNow(jdbc);
        Invoice overlapSpare = installmentDueOn(d(2118, 4, 12), ars(80000));
        Invoice crossSpare = installmentDueOn(d(2118, 5, 12), ars(80000));
        Invoice beforeDueSpare = installmentDueOn(d(2118, 6, 12), ars(80000));
        Invoice disabledSpare = installmentDueOn(d(2118, 7, 12), ars(80000));
        Invoice overDebitNoteSpare = installmentDueOn(d(2118, 8, 12), ars(80000));
        Invoice frozenSpare = installmentDueOn(d(2118, 9, 12), ars(80000));
        Invoice reviewOrigin = BillingTestFixtures.invoice().forCourseEnrollment(enrollment).forPeriod(YearMonth.of(2118, 10)).dueOn(d(2118, 10, 12)).withBaseAmount(ars(80000))
                .customizedBy(created -> created.markPaymentUnderReview(d(2118, 10, 1), ACTOR, "Comprobante a tiempo")).build();
        Invoice reviewSpare = installmentDueOn(d(2118, 11, 12), ars(80000));
        Map<String, Object> charge = chargesOf(origin).get(0);
        Long accountId = reload(origin).getStudentCurrentAccountId();
        Long versionId = (Long) charge.get("late_payment_version_id");
        Long debitNoteId = (Long) charge.get("debit_note_id");
        try {
            dropConstraint("ex_late_fee_charge_no_overlapping_days");
            dropConstraint("ck_late_fee_charge_period_one_month");
            insertRawCharge(overlapSpare.getId(), origin.getId(), accountId, d(2118, 3, 20), d(2118, 3, 25), 6, versionId);
            insertRawCharge(crossSpare.getId(), origin.getId(), accountId, d(2118, 5, 25), d(2118, 6, 2), 9, versionId);
            insertRawCharge(beforeDueSpare.getId(), origin.getId(), accountId, d(2118, 3, 5), d(2118, 3, 8), 4, versionId);
            insertRawCharge(disabledSpare.getId(), origin.getId(), accountId, d(2118, 6, 10), d(2118, 6, 15), 6, versionId);
            insertRawCharge(overDebitNoteSpare.getId(), debitNoteId, accountId, d(2118, 4, 20), d(2118, 4, 25), 6, versionId);
            withdrawCourseEnrollmentOn(d(2118, 3, 20));
            insertRawCharge(frozenSpare.getId(), origin.getId(), accountId, d(2118, 3, 26), d(2118, 3, 28), 3, versionId);
            insertRawCharge(reviewSpare.getId(), reviewOrigin.getId(), accountId, d(2118, 10, 20), d(2118, 10, 25), 6, versionId);

            List<String> violations = clean.lateFeesRespectTheRedefinedI11();

            assertThat(violations).anyMatch(v -> v.startsWith("I-11a") && v.contains("origin " + origin.getId()));
            assertThat(violations).anyMatch(v -> v.startsWith("I-11b a period crosses months") && v.contains("debit note " + crossSpare.getId()));
            assertThat(violations).anyMatch(v -> v.startsWith("I-11b a period crosses months") && v.contains("debit note " + beforeDueSpare.getId()));
            assertThat(violations).anyMatch(v -> v.startsWith("I-11b charged days") && v.contains("debit note " + disabledSpare.getId()));
            assertThat(violations).anyMatch(v -> v.startsWith("I-11d") && v.contains("debit note " + overDebitNoteSpare.getId()));
            assertThat(violations).anyMatch(v -> v.startsWith("I-11e") && v.contains("debit note " + frozenSpare.getId()));
            assertThat(violations).anyMatch(v -> v.startsWith("I-11f") && v.contains("debit note " + overlapSpare.getId()));
            assertThat(violations).anyMatch(v -> v.startsWith("I-11g") && v.contains("debit note " + reviewSpare.getId()));
            assertThat(violations).noneMatch(v -> v.contains("debit note " + debitNoteId));
        } finally {
            jdbc.update("delete from late_fee_charge where debit_note_id in (?, ?, ?, ?, ?, ?, ?)", overlapSpare.getId(), crossSpare.getId(), beforeDueSpare.getId(),
                    disabledSpare.getId(), overDebitNoteSpare.getId(), frozenSpare.getId(), reviewSpare.getId());
            jdbc.execute("alter table late_fee_charge add constraint ck_late_fee_charge_period_one_month check (date_trunc('month', period_start) = date_trunc('month', period_end))");
            jdbc.execute("alter table late_fee_charge add constraint ex_late_fee_charge_no_overlapping_days exclude using gist (origin_invoice_id with =, "
                    + "daterange(period_start, period_end, '[]') with &&)");
        }
        clean.assertAllBillingInvariantsHold();
    }

    @Test
    @Tag("D-293")
    @Tag("D-148")
    void aCourseFreezesItsLateFeesAtTheEndOfItsTermAndTheEndDayStillCharges() {
        arrange(2119);
        publishDefaults(2119);
        term.setEndDate(LocalDateTime.of(2119, 3, 20, 0, 0));
        termRepository.save(term);
        Invoice origin = installmentDueOn(d(2119, 3, 11), ars(100000));

        reconcile(origin, d(2119, 6, 1));

        assertThat(summaryOf(origin)).containsExactly("2119-03-12..2119-03-20:9:1800.00");
    }

    @Test
    @Tag("D-293")
    @Tag("D-148")
    void aCareerWithdrawalForArrearsFreezesTheLateFeesAtItsDate() {
        arrange(2120);
        publishDefaults(2120);
        ProgramEnrollment programEnrollment = programEnrollment();
        Invoice origin = programInstallmentDueOn(programEnrollment, d(2120, 3, 11));
        programEnrollment.setStatus(ProgramEnrollmentStatus.WITHDRAWN);
        programEnrollment.setCompletionDate(LocalDateTime.of(2120, 3, 20, 9, 0));
        programEnrollmentRepository.save(programEnrollment);

        reconcile(origin, d(2120, 6, 1));

        assertThat(summaryOf(origin)).containsExactly("2120-03-12..2120-03-20:9:1800.00");
    }

    @Test
    @Tag("D-293")
    @Tag("D-148")
    void graduatingFreezesTheLateFeesOfTheCareerAtTheGraduationDate() {
        arrange(2121);
        publishDefaults(2121);
        ProgramEnrollment programEnrollment = programEnrollment();
        Invoice origin = programInstallmentDueOn(programEnrollment, d(2121, 3, 11));
        programEnrollment.complete(LocalDateTime.of(2121, 3, 20, 9, 0));
        programEnrollmentRepository.save(programEnrollment);

        reconcile(origin, d(2121, 6, 1));

        assertThat(summaryOf(origin)).containsExactly("2121-03-12..2121-03-20:9:1800.00");
    }

    @Test
    @Tag("D-293")
    @Tag("D-148")
    void aCourseEndedByApprovalFreezesItsLateFees() {
        arrange(2122);
        publishDefaults(2122);
        Invoice origin = installmentDueOn(d(2122, 3, 11), ars(100000));
        enrollment.setStatus(EnrollmentStatus.APPROVED);
        enrollment.setCompletionDate(LocalDateTime.of(2122, 3, 20, 10, 0));
        courseEnrollmentRepository.save(enrollment);

        reconcile(origin, d(2122, 6, 1));

        assertThat(summaryOf(origin)).containsExactly("2122-03-12..2122-03-20:9:1800.00");
    }

    @Test
    @Tag("D-332")
    @Tag("D-330")
    void everyDayRunsAndASingleLateRunProduceTheSamePeriods() {
        arrange(2123);
        publishDefaults(2123);
        Invoice everyDay = installmentDueOn(d(2123, 3, 11), ars(100000));
        Invoice once = installmentDueOn(d(2123, 3, 11), ars(100000));

        reconcileEveryDay(everyDay, d(2123, 3, 11), d(2123, 7, 3));
        reconcile(once, d(2123, 7, 3));

        assertThat(summaryOf(everyDay)).containsExactly("2123-03-12..2123-03-31:20:4000.00", "2123-04-01..2123-04-30:30:6000.00", "2123-05-01..2123-05-31:31:6200.00",
                "2123-06-01..2123-06-30:30:6000.00");
        assertThat(summaryOf(once)).isEqualTo(summaryOf(everyDay));
    }
}
