package com.red_escuela.backend.billing.collections.service;

import com.red_escuela.backend.academicstructure.entity.Course;
import com.red_escuela.backend.academicstructure.repository.CourseRepository;
import com.red_escuela.backend.billing.api.EnrollmentPaymentStandingQuery;
import com.red_escuela.backend.billing.api.EnrollmentPaymentStatusChangeView;
import com.red_escuela.backend.billing.collections.domain.EnrollmentKind;
import com.red_escuela.backend.billing.collections.domain.EnrollmentSuspensionRule;
import com.red_escuela.backend.billing.collections.dto.EnrollmentPaymentStatusChangeResponse;
import com.red_escuela.backend.billing.collections.exception.EnrollmentPaymentStandingNotFoundException;
import com.red_escuela.backend.billing.collections.operation.EnrollmentPaymentStandingStep;
import com.red_escuela.backend.billing.configuration.domain.LatePaymentTerms;
import com.red_escuela.backend.billing.entity.EnrollmentPaymentStatus;
import com.red_escuela.backend.billing.invoice.domain.FeeType;
import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.billing.invoice.domain.event.InvoicePaidDomainEvent;
import com.red_escuela.backend.billing.service.LatePaymentTestFixtures;
import com.red_escuela.backend.billing.support.BillingTestFixtures;
import com.red_escuela.backend.enrollment.entity.CourseEnrollment;
import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.enrollment.repository.CourseEnrollmentRepository;
import com.red_escuela.backend.shared.domain.StatusType;
import com.red_escuela.backend.billing.account.domain.CurrentAccountEntry;
import com.red_escuela.backend.shared.time.MutableClock;
import com.red_escuela.backend.billing.invoice.domain.event.InvoiceCancelledEvent;
import com.red_escuela.backend.billing.note.domain.CancelInvoiceCommand;
import com.red_escuela.backend.billing.note.domain.CreditNoteDestination;
import com.red_escuela.backend.billing.note.domain.CreditNoteLineAmount;
import com.red_escuela.backend.billing.note.domain.CreditNoteReason;
import com.red_escuela.backend.billing.note.domain.IssueCreditNoteCommand;
import com.red_escuela.backend.billing.note.service.CancelInvoiceOperation;
import com.red_escuela.backend.billing.note.service.IssueCreditNoteOperation;
import com.red_escuela.backend.billing.operation.BillingOperationPipeline;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.modulith.events.IncompleteEventPublications;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static com.red_escuela.backend.shared.domain.TestMoney.ars;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EnrollmentPaymentStandingIT extends AbstractLateFeeScenarioIT {
    private static final int YEAR = 2027;
    private static final int POLICY_YEAR = 2031;
    private static final int GRACE_YEAR = 2032;
    private static final int GRACE_SUSPENSION_YEAR = 2033;
    private static final int TWO_LOST_MONTHS_YEAR = 2034;
    private static final int THREE_LOST_MONTHS_YEAR = 2035;
    private static final int NONPAYMENT_NET_YEAR = 2036;
    private static final int NONPAYMENT_PAYMENT_YEAR = 2037;
    private static final EnrollmentSuspensionRule END_OF_MONTH = new EnrollmentSuspensionRule(1, 0);

    @Autowired
    private EnrollmentPaymentStandingService standingService;
    @Autowired
    private EnrollmentPaymentStandingStep standingStep;
    @Autowired
    private EnrollmentPaymentStandingQuery standingQuery;
    @Autowired
    private EnrollmentPaymentStandingReader reader;
    @Autowired
    private BillingOperationPipeline pipeline;
    @Autowired
    private IssueCreditNoteOperation creditNoteOperation;
    @Autowired
    private CancelInvoiceOperation cancelInvoiceOperation;
    @Autowired
    private IncompleteEventPublications incompleteEventPublications;
    @Autowired
    private CourseRepository courseRepository;
    @Autowired
    private CourseEnrollmentRepository courseEnrollmentRepository;

    @AfterEach
    void restoreTheClock() {
        MutableClock.INSTANCE.reset();
    }

    private CurrentAccountEntry payInFullAndAwaitEvents(Invoice origin, LocalDate paymentDate, LocalDate allocationDate) {
        CurrentAccountEntry credit = payInFull(origin, paymentDate, allocationDate);
        awaitEventsDrained();
        return credit;
    }

    private Long submitReceipt(Invoice installment, LocalDate uploadDate, boolean protectsFromOverdue) {
        String operationNumber = "OP-" + System.nanoTime();
        java.sql.Timestamp uploadedAt = java.sql.Timestamp.valueOf(uploadDate.atTime(10, 0));
        Long paymentId = jdbc.queryForObject("insert into payment (student_current_account_id, currency, status, gateway, method_type, amount, amount_currency, "
                + "refunded_amount, refunded_amount_currency, registered_at, started_at, method_details, registered_by_process) "
                + "select a.id, a.currency, 'PENDING', 'MANUAL', 'BANK_TRANSFER', 100000, a.currency, 0, a.currency, ?, ?, "
                + "cast(? as jsonb), 'TEST' from student_current_account a where a.id = ? returning id", Long.class,
                uploadedAt, uploadedAt,
                "{\"type\":\"BANK_TRANSFER\",\"operationNumber\":\"" + operationNumber + "\",\"transferDate\":\"" + uploadDate + "\"}",
                installment.getStudentCurrentAccountId());
        jdbc.update("insert into payment_checkout_item (payment_id, student_current_account_id, invoice_id, position, outstanding_at_selection, "
                + "outstanding_at_selection_currency, marked_under_review) select ?, a.id, ?, 1, 100000, a.currency, ? from student_current_account a where a.id = ?",
                paymentId, installment.getId(), protectsFromOverdue, installment.getStudentCurrentAccountId());
        jdbc.update("insert into payment_status_change (payment_id, event, from_status, to_status, actor_process, occurred_at) values (?, 'PAYMENT_STARTED', null, 'PENDING', 'TEST', ?)",
                paymentId, uploadedAt);
        if (protectsFromOverdue) {
            jdbc.update("update invoice set payment_status = 'PAYMENT_UNDER_REVIEW' where id = ?", installment.getId());
        }
        return paymentId;
    }

    private void changePaymentStatus(Long paymentId, String event, String toStatus, LocalDate on) {
        jdbc.update("update payment set status = ? where id = ?", toStatus, paymentId);
        jdbc.update("insert into payment_status_change (payment_id, event, from_status, to_status, actor_process, occurred_at) values (?, ?, 'PENDING', ?, 'TEST', ?)",
                paymentId, event, toStatus, java.sql.Timestamp.valueOf(on.atTime(11, 0)));
    }

    private long pendingStandingPublications() {
        return jdbc.queryForObject("select count(*) from event_publication where completion_date is null and listener_id like '%EnrollmentPaymentStandingOnInvoicePaidListener%'", Long.class);
    }

    private void runStepFor(LocalDate businessDate) {
        standingStep.evaluateEnrollmentsWithIds(EnrollmentKind.COURSE, List.of(enrollment.getId()), businessDate, LatePaymentTerms.initial());
    }

    private void evaluate(LocalDate businessDate) {
        evaluate(businessDate, END_OF_MONTH);
    }

    private void evaluate(LocalDate businessDate, EnrollmentSuspensionRule rule) {
        standingService.evaluateEnrollmentStanding(EnrollmentKind.COURSE, enrollment.getId(), businessDate, cutoff -> rule);
    }

    private EnrollmentPaymentStatus statusOf(Long enrollmentId) {
        return standingQuery.findStandingOfCourseEnrollment(enrollmentId).orElseThrow().status();
    }

    private Invoice paidInstallmentDueOn(LocalDate dueDate) {
        return BillingTestFixtures.invoice().forCourseEnrollment(enrollment).forPeriod(YearMonth.from(dueDate)).dueOn(dueDate).withBaseAmount(ars(100000)).paid().build();
    }

    private Invoice overdueLateFeeOf(Invoice origin, LocalDate dueDate) {
        Invoice lateFee = overdueInstallmentDueOn(dueDate);
        jdbc.update("update invoice set document_type = 'DEBIT_NOTE', invoice_type = 'LATE_FEE', origin_invoice_id = ? where id = ?", origin.getId(), lateFee.getId());
        return lateFee;
    }

    private List<String> eventsOf(Long enrollmentId) {
        return standingQuery.listStatusChangesOfCourseEnrollment(enrollmentId).stream().map(EnrollmentPaymentStatusChangeView::event).toList();
    }

    private void assertTheHistoryChainIsConsistent(Long enrollmentId) {
        List<EnrollmentPaymentStatusChangeView> history = standingQuery.listStatusChangesOfCourseEnrollment(enrollmentId);
        assertThat(history.getFirst().fromStatus()).isNull();
        for (int i = 1; i < history.size(); i++) {
            assertThat(history.get(i).fromStatus()).as("change %d starts where change %d ended", i, i - 1).isEqualTo(history.get(i - 1).toStatus());
        }
        assertThat(history.getLast().toStatus()).isEqualTo(statusOf(enrollmentId));
    }

    private void publishPolicy(int graceDays, int suspensionAfterDays, int suspensionAfterInvoiceCount, LocalDate effectiveFrom) {
        LatePaymentTestFixtures.persistLatePayment(configurationVersionRepository, person, graceDays, new BigDecimal("0.002"), suspensionAfterDays,
                suspensionAfterInvoiceCount, effectiveFrom);
    }

    private <T> void runInParallel(List<Callable<T>> tasks) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(tasks.size());
        try {
            for (Future<T> future : executor.invokeAll(tasks)) {
                future.get();
            }
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    @Tag("D-241")
    @Tag("D-306")
    void aMonthlyInstallmentUnpaidAtMonthCloseSuspendsOnTheFirstOfTheNextMonthAndNotBefore() {
        arrange(YEAR);
        overdueInstallmentDueOn(d(YEAR, 5, 10));

        evaluate(d(YEAR, 5, 20));
        assertThat(statusOf(enrollment.getId())).isEqualTo(EnrollmentPaymentStatus.OVERDUE);
        evaluate(d(YEAR, 5, 31));
        assertThat(statusOf(enrollment.getId())).isEqualTo(EnrollmentPaymentStatus.OVERDUE);
        evaluate(d(YEAR, 6, 1));

        assertThat(statusOf(enrollment.getId())).isEqualTo(EnrollmentPaymentStatus.SUSPENDED);
        assertThat(standingQuery.findStandingOfCourseEnrollment(enrollment.getId()).orElseThrow().suspendedSince()).isEqualTo(d(YEAR, 6, 1));
        evaluate(d(YEAR, 6, 1));
        assertThat(statusOf(enrollment.getId())).isEqualTo(EnrollmentPaymentStatus.SUSPENDED);
        assertThat(eventsOf(enrollment.getId())).containsExactly("STANDING_OPENED", "OVERDUE_DEBT_DETECTED", "MONTH_CLOSED_WITH_UNPAID_INSTALLMENT");
        assertTheHistoryChainIsConsistent(enrollment.getId());
    }

    @Test
    @Tag("D-473")
    void anInstallmentPaidWithAnEffectiveDateOfTheLastDayOfTheMonthDoesNotSuspendEvenIfRegisteredOnTheFirst() {
        arrange(YEAR);
        Invoice installment = overdueInstallmentDueOn(d(YEAR, 5, 10));
        evaluate(d(YEAR, 5, 20));

        receiveMoney(installment, "100000.00", d(YEAR, 5, 31));
        allocateOn(installment, d(YEAR, 6, 1));
        awaitEventsDrained();
        evaluate(d(YEAR, 6, 1));

        assertThat(statusOf(enrollment.getId())).isEqualTo(EnrollmentPaymentStatus.UP_TO_DATE);
        assertThat(eventsOf(enrollment.getId())).doesNotContain("MONTH_CLOSED_WITH_UNPAID_INSTALLMENT");
    }

    @Test
    @Tag("D-473")
    void anInstallmentPaidJustAfterTheCutoffSuspendsAtTheClosureAndIsSettledRightAfter() {
        arrange(YEAR);
        Invoice installment = overdueInstallmentDueOn(d(YEAR, 5, 10));
        evaluate(d(YEAR, 5, 20));

        receiveMoney(installment, "100000.00", d(YEAR, 6, 1));
        allocateOn(installment, d(YEAR, 6, 1));
        awaitEventsDrained();
        evaluate(d(YEAR, 6, 1));

        assertThat(eventsOf(enrollment.getId())).contains("MONTH_CLOSED_WITH_UNPAID_INSTALLMENT");
        assertThat(eventsOf(enrollment.getId()).getLast()).isEqualTo("ACCOUNT_SETTLED");
        assertThat(statusOf(enrollment.getId())).isEqualTo(EnrollmentPaymentStatus.UP_TO_DATE);
        assertTheHistoryChainIsConsistent(enrollment.getId());
    }

    @Test
    @Tag("D-473")
    @Tag("D-288")
    void anInstallmentDueOnTheCutoffDayOrStillInsideItsGraceIsNotOverdueAtTheCutoff() {
        arrange(GRACE_YEAR);
        publishPolicy(5, 0, 1, d(GRACE_YEAR, 1, 1));
        pendingInstallmentDueOn(d(GRACE_YEAR, 5, 31));
        pendingInstallmentDueOn(d(GRACE_YEAR, 5, 28));
        evaluate(d(GRACE_YEAR, 5, 20));

        evaluate(d(GRACE_YEAR, 6, 1));

        assertThat(statusOf(enrollment.getId())).isEqualTo(EnrollmentPaymentStatus.UP_TO_DATE);
        assertThat(eventsOf(enrollment.getId())).doesNotContain("MONTH_CLOSED_WITH_UNPAID_INSTALLMENT");
    }

    @Test
    @Tag("D-473")
    @Tag("D-288")
    void anInstallmentWhoseGraceEndedBeforeTheCutoffSuspends() {
        arrange(GRACE_SUSPENSION_YEAR);
        publishPolicy(5, 0, 1, d(GRACE_SUSPENSION_YEAR, 1, 1));
        pendingInstallmentDueOn(d(GRACE_SUSPENSION_YEAR, 5, 25));
        evaluate(d(GRACE_SUSPENSION_YEAR, 5, 20));

        evaluate(d(GRACE_SUSPENSION_YEAR, 6, 1));

        assertThat(eventsOf(enrollment.getId())).contains("MONTH_CLOSED_WITH_UNPAID_INSTALLMENT");
    }

    @Test
    @Tag("D-473")
    @Tag("D-306")
    void aMissedFirstOfTheMonthIsRecoveredOnTheFirstNightThatRunsUsingThePolicyOfTheCutoffAndNeverEvaluatesTheSameMonthTwice() {
        arrange(POLICY_YEAR);
        publishPolicy(0, 0, 1, d(POLICY_YEAR, 1, 1));
        publishPolicy(0, 90, 1, d(POLICY_YEAR, 6, 1));
        overdueInstallmentDueOn(d(POLICY_YEAR, 5, 10));
        MutableClock.INSTANCE.setDate(d(POLICY_YEAR, 5, 20));
        runStepFor(d(POLICY_YEAR, 5, 20));
        assertThat(statusOf(enrollment.getId())).isEqualTo(EnrollmentPaymentStatus.OVERDUE);

        MutableClock.INSTANCE.setDate(d(POLICY_YEAR, 6, 3));
        runStepFor(d(POLICY_YEAR, 6, 3));

        assertThat(statusOf(enrollment.getId())).isEqualTo(EnrollmentPaymentStatus.SUSPENDED);
        List<EnrollmentPaymentStatusChangeView> history = standingQuery.listStatusChangesOfCourseEnrollment(enrollment.getId());
        assertThat(history.getLast().event()).isEqualTo("MONTH_CLOSED_WITH_UNPAID_INSTALLMENT");
        assertThat(history.getLast().businessDate()).isEqualTo(d(POLICY_YEAR, 6, 3));
        int changes = history.size();
        runStepFor(d(POLICY_YEAR, 6, 4));
        runStepFor(d(POLICY_YEAR, 6, 5));
        assertThat(standingQuery.listStatusChangesOfCourseEnrollment(enrollment.getId())).hasSize(changes);
        assertThat(jdbc.queryForObject("select last_closed_month::text from enrollment_payment_standing where course_enrollment_id = ?", String.class, enrollment.getId()))
                .isEqualTo(d(POLICY_YEAR, 5, 1).toString());
    }

    @Test
    @Tag("D-241")
    @Tag("D-061")
    @Tag("D-292")
    void anUnpaidLateFeeAloneLeavesTheEnrollmentOverdueAtMonthCloseButNeverSuspended() {
        arrange(YEAR);
        Invoice paid = paidInstallmentDueOn(d(YEAR, 5, 10));
        overdueLateFeeOf(paid, d(YEAR, 5, 20));
        evaluate(d(YEAR, 5, 20));

        evaluate(d(YEAR, 6, 1));

        assertThat(statusOf(enrollment.getId())).isEqualTo(EnrollmentPaymentStatus.OVERDUE);
    }

    @Test
    @Tag("D-241")
    @Tag("D-292")
    @Tag("D-307")
    @Tag("D-122")
    void aSuspendedEnrollmentStaysSuspendedUntilTheInstallmentAndTheLateFeeArePaidAndTheHistoryRecordsEveryStep() {
        arrange(YEAR);
        Invoice installment = overdueInstallmentDueOn(d(YEAR, 5, 10));
        Invoice lateFee = overdueLateFeeOf(installment, d(YEAR, 5, 20));
        evaluate(d(YEAR, 5, 20));
        evaluate(d(YEAR, 6, 1));
        assertThat(statusOf(enrollment.getId())).isEqualTo(EnrollmentPaymentStatus.SUSPENDED);

        payInFullAndAwaitEvents(installment, d(YEAR, 6, 5), d(YEAR, 6, 5));
        assertThat(statusOf(enrollment.getId())).isEqualTo(EnrollmentPaymentStatus.SUSPENDED);

        payInFullAndAwaitEvents(lateFee, d(YEAR, 6, 6), d(YEAR, 6, 6));
        assertThat(statusOf(enrollment.getId())).isEqualTo(EnrollmentPaymentStatus.UP_TO_DATE);
        assertThat(standingQuery.findStandingOfCourseEnrollment(enrollment.getId()).orElseThrow().suspendedSince()).isNull();
        assertThat(eventsOf(enrollment.getId())).containsExactly("STANDING_OPENED", "OVERDUE_DEBT_DETECTED", "MONTH_CLOSED_WITH_UNPAID_INSTALLMENT", "ACCOUNT_SETTLED");
        List<EnrollmentPaymentStatusChangeView> history = standingQuery.listStatusChangesOfCourseEnrollment(enrollment.getId());
        assertThat(history.get(2).businessDate()).isEqualTo(d(YEAR, 6, 1));
        assertThat(history.get(2).actorProcess()).isEqualTo(EnrollmentPaymentStandingService.STANDING_PROCESS);
        assertThat(history.get(2).reason()).isNotBlank();
        assertThat(history.get(3).businessDate()).isEqualTo(d(YEAR, 6, 6));
        assertTheHistoryChainIsConsistent(enrollment.getId());
    }

    @Test
    @Tag("D-185")
    void aFailureOfTheStandingAfterThePaymentNeverRevertsThePayment() {
        arrange(YEAR);
        Invoice installment = overdueInstallmentDueOn(d(YEAR, 5, 10));
        evaluate(d(YEAR, 5, 20));
        evaluate(d(YEAR, 6, 1));
        Long standingId = jdbc.queryForObject("select id from enrollment_payment_standing where course_enrollment_id = ?", Long.class, enrollment.getId());
        jdbc.execute("create or replace function fail_standing_history_for_test() returns trigger language plpgsql as $$ begin raise exception 'forced failure'; end $$");
        jdbc.execute("create trigger trg_fail_standing_history_for_test before insert on enrollment_payment_status_change for each row "
                + "when (new.standing_id = " + standingId + ") execute function fail_standing_history_for_test()");
        try {
            payInFull(installment, d(YEAR, 6, 5), d(YEAR, 6, 5));
            await().atMost(Duration.ofSeconds(25)).until(() -> pendingStandingPublications() == 1L);
        } finally {
            jdbc.execute("drop trigger trg_fail_standing_history_for_test on enrollment_payment_status_change");
        }

        assertThat(jdbc.queryForObject("select payment_status from invoice where id = ?", String.class, installment.getId())).isEqualTo("PAID");
        assertThat(statusOf(enrollment.getId())).isEqualTo(EnrollmentPaymentStatus.SUSPENDED);
        standingService.reevaluateExistingStanding(EnrollmentKind.COURSE, enrollment.getId(), d(YEAR, 6, 6));
        assertThat(statusOf(enrollment.getId())).isEqualTo(EnrollmentPaymentStatus.UP_TO_DATE);
    }

    @Test
    @Tag("D-146")
    void aBalanceOwedForAnInstallmentThatIsNotDueYetKeepsTheEnrollmentUpToDate() {
        arrange(YEAR);
        pendingInstallmentDueOn(d(YEAR, 7, 10));
        evaluate(d(YEAR, 5, 20));

        evaluate(d(YEAR, 6, 1));

        assertThat(statusOf(enrollment.getId())).isEqualTo(EnrollmentPaymentStatus.UP_TO_DATE);
    }

    @Test
    @Tag("D-241")
    @Tag("D-146")
    @Tag("D-474")
    void anOverdueInvoiceThatIsNotAMonthlyInstallmentMakesTheEnrollmentOverdueButNeverSuspendsIt() {
        arrange(YEAR);
        BillingTestFixtures.invoice().forCourseEnrollment(enrollment).ofType(FeeType.ADMINISTRATIVE_FEE).forPeriod(YearMonth.of(YEAR, 5)).dueOn(d(YEAR, 5, 10))
                .withBaseAmount(ars(5000)).overdue().build();
        evaluate(d(YEAR, 5, 20));

        evaluate(d(YEAR, 6, 1));

        assertThat(statusOf(enrollment.getId())).isEqualTo(EnrollmentPaymentStatus.OVERDUE);
    }

    @Test
    @Tag("D-065")
    void overdueDebtOfOneEnrollmentDoesNotChangeTheOtherEnrollmentOfTheSameStudent() {
        arrange(YEAR);
        Course otherCourse = new Course();
        otherCourse.setName("Otro curso " + System.nanoTime());
        otherCourse.setTerm(term);
        otherCourse.setIsActive(StatusType.ACTIVE);
        otherCourse = courseRepository.save(otherCourse);
        CourseEnrollment other = new CourseEnrollment();
        other.setStudent(student);
        other.setCourse(otherCourse);
        other.setStatus(EnrollmentStatus.ENROLLED);
        other.setEnrollmentDate(LocalDateTime.now());
        other = courseEnrollmentRepository.save(other);
        overdueInstallmentDueOn(d(YEAR, 5, 10));
        evaluate(d(YEAR, 5, 20));
        standingService.evaluateEnrollmentStanding(EnrollmentKind.COURSE, other.getId(), d(YEAR, 5, 20), cutoff -> END_OF_MONTH);

        evaluate(d(YEAR, 6, 1));
        standingService.evaluateEnrollmentStanding(EnrollmentKind.COURSE, other.getId(), d(YEAR, 6, 1), cutoff -> END_OF_MONTH);

        assertThat(statusOf(enrollment.getId())).isEqualTo(EnrollmentPaymentStatus.SUSPENDED);
        assertThat(statusOf(other.getId())).isEqualTo(EnrollmentPaymentStatus.UP_TO_DATE);
    }

    @Test
    @Tag("D-185")
    void theNightlyStepOpensTheStandingOfEveryActiveEnrollmentAndEvaluatesIt() {
        arrange(YEAR);
        overdueInstallmentDueOn(d(YEAR, 5, 10));

        EnrollmentPaymentStandingStep.Result opening = standingStep.evaluateEnrollments(d(YEAR, 5, 20), LatePaymentTerms.initial());
        EnrollmentPaymentStandingStep.Result closing = standingStep.evaluateEnrollments(d(YEAR, 6, 1), LatePaymentTerms.initial());

        assertThat(opening.processed()).isPositive();
        assertThat(closing.candidates()).isEqualTo(closing.processed() + closing.failed());
        assertThat(statusOf(enrollment.getId())).isEqualTo(EnrollmentPaymentStatus.SUSPENDED);
    }

    @Test
    @Tag("D-185")
    @Tag("D-142")
    void anEnrollmentThatEndedWithOverdueDebtKeepsBeingEvaluatedUntilItPaysButNeverSuspends() {
        arrange(YEAR);
        Invoice installment = overdueInstallmentDueOn(d(YEAR, 5, 10));
        evaluate(d(YEAR, 5, 20));
        withdrawCourseEnrollmentOn(d(YEAR, 5, 25));

        standingStep.evaluateEnrollments(d(YEAR, 6, 1), LatePaymentTerms.initial());

        assertThat(statusOf(enrollment.getId())).isEqualTo(EnrollmentPaymentStatus.OVERDUE);
        assertThat(eventsOf(enrollment.getId())).doesNotContain("MONTH_CLOSED_WITH_UNPAID_INSTALLMENT");
        payInFullAndAwaitEvents(installment, d(YEAR, 6, 5), d(YEAR, 6, 5));
        assertThat(statusOf(enrollment.getId())).isEqualTo(EnrollmentPaymentStatus.UP_TO_DATE);
    }

    @Test
    @Tag("D-185")
    void aClosureOfTheTermRecordsTheWithdrawalOnlyWhenTheStandingWasSuspended() {
        arrange(YEAR);
        overdueInstallmentDueOn(d(YEAR, 5, 10));
        evaluate(d(YEAR, 5, 20));

        standingService.recordTermClosedWhileSuspended(EnrollmentKind.COURSE, enrollment.getId(), d(YEAR, 12, 20));
        assertThat(statusOf(enrollment.getId())).isEqualTo(EnrollmentPaymentStatus.OVERDUE);

        evaluate(d(YEAR, 6, 1));
        standingService.recordTermClosedWhileSuspended(EnrollmentKind.COURSE, enrollment.getId(), d(YEAR, 12, 20));
        assertThat(statusOf(enrollment.getId())).isEqualTo(EnrollmentPaymentStatus.WITHDRAWN_FOR_NONPAYMENT);
        assertThat(standingQuery.listStatusChangesOfCourseEnrollment(enrollment.getId()).getLast().actorProcess())
                .isEqualTo(EnrollmentPaymentStandingService.LEGACY_TERM_CLOSURE_PROCESS);
        assertTheHistoryChainIsConsistent(enrollment.getId());
    }

    @Test
    @Tag("D-473")
    @Tag("D-306")
    void twoMonthsLostByAnOutageAreEvaluatedOneByOneEachWithTheFactsOfItsCutoff() {
        arrange(TWO_LOST_MONTHS_YEAR);
        publishPolicy(0, 0, 1, d(TWO_LOST_MONTHS_YEAR, 1, 1));
        Invoice installment = overdueInstallmentDueOn(d(TWO_LOST_MONTHS_YEAR, 5, 10));
        MutableClock.INSTANCE.setDate(d(TWO_LOST_MONTHS_YEAR, 5, 20));
        runStepFor(d(TWO_LOST_MONTHS_YEAR, 5, 20));
        receiveMoney(installment, "100000.00", d(TWO_LOST_MONTHS_YEAR, 7, 2));
        allocateOn(installment, d(TWO_LOST_MONTHS_YEAR, 7, 2));
        awaitEventsDrained();

        MutableClock.INSTANCE.setDate(d(TWO_LOST_MONTHS_YEAR, 7, 3));
        runStepFor(d(TWO_LOST_MONTHS_YEAR, 7, 3));

        assertThat(eventsOf(enrollment.getId())).containsExactly("STANDING_OPENED", "OVERDUE_DEBT_DETECTED", "OVERDUE_DEBT_CLEARED",
                "OVERDUE_DEBT_DETECTED", "MONTH_CLOSED_WITH_UNPAID_INSTALLMENT", "ACCOUNT_SETTLED", "OVERDUE_DEBT_DETECTED", "MONTH_CLOSED_WITH_UNPAID_INSTALLMENT", "ACCOUNT_SETTLED");
        assertThat(lastClosedMonthOf(enrollment.getId())).isEqualTo(d(TWO_LOST_MONTHS_YEAR, 6, 1).toString());
        assertTheHistoryChainIsConsistent(enrollment.getId());
        int changes = eventsOf(enrollment.getId()).size();
        runStepFor(d(TWO_LOST_MONTHS_YEAR, 7, 4));
        assertThat(eventsOf(enrollment.getId())).hasSize(changes);
    }

    @Test
    @Tag("D-473")
    @Tag("D-306")
    void threeMonthsLostByAnOutageUseThePolicyOfEachCutoffAndStopAtTheLastClosedMonth() {
        arrange(THREE_LOST_MONTHS_YEAR);
        publishPolicy(0, 0, 1, d(THREE_LOST_MONTHS_YEAR, 1, 1));
        publishPolicy(0, 0, 5, d(THREE_LOST_MONTHS_YEAR, 6, 1));
        Invoice installment = overdueInstallmentDueOn(d(THREE_LOST_MONTHS_YEAR, 5, 10));
        MutableClock.INSTANCE.setDate(d(THREE_LOST_MONTHS_YEAR, 5, 20));
        runStepFor(d(THREE_LOST_MONTHS_YEAR, 5, 20));
        receiveMoney(installment, "100000.00", d(THREE_LOST_MONTHS_YEAR, 7, 20));
        allocateOn(installment, d(THREE_LOST_MONTHS_YEAR, 7, 20));
        awaitEventsDrained();

        MutableClock.INSTANCE.setDate(d(THREE_LOST_MONTHS_YEAR, 8, 3));
        runStepFor(d(THREE_LOST_MONTHS_YEAR, 8, 3));

        assertThat(eventsOf(enrollment.getId()).stream().filter("MONTH_CLOSED_WITH_UNPAID_INSTALLMENT"::equals).count()).isEqualTo(1);
        assertThat(statusOf(enrollment.getId())).isEqualTo(EnrollmentPaymentStatus.UP_TO_DATE);
        assertThat(lastClosedMonthOf(enrollment.getId())).isEqualTo(d(THREE_LOST_MONTHS_YEAR, 7, 1).toString());
        assertTheHistoryChainIsConsistent(enrollment.getId());
        int changes = eventsOf(enrollment.getId()).size();
        runStepFor(d(THREE_LOST_MONTHS_YEAR, 8, 4));
        assertThat(eventsOf(enrollment.getId())).hasSize(changes);
    }

    @Test
    @Tag("D-185")
    @Tag("D-306")
    void theNightlyStepClosesTheSuspendedStandingOfAnEnrollmentWithdrawnForNonpaymentWhenTheClosureEventWasLost() {
        arrange(NONPAYMENT_NET_YEAR);
        overdueInstallmentDueOn(d(NONPAYMENT_NET_YEAR, 5, 10));
        evaluate(d(NONPAYMENT_NET_YEAR, 5, 20));
        evaluate(d(NONPAYMENT_NET_YEAR, 6, 1));
        assertThat(statusOf(enrollment.getId())).isEqualTo(EnrollmentPaymentStatus.SUSPENDED);
        jdbc.update("update course_enrollment set status = 'WITHDRAWN', payment_status = 'WITHDRAWN_FOR_NONPAYMENT' where id = ?", enrollment.getId());

        runStepFor(d(NONPAYMENT_NET_YEAR, 6, 3));

        assertThat(statusOf(enrollment.getId())).isEqualTo(EnrollmentPaymentStatus.WITHDRAWN_FOR_NONPAYMENT);
        assertThat(eventsOf(enrollment.getId()).getLast()).isEqualTo("TERM_CLOSED_WHILE_SUSPENDED");
        assertTheHistoryChainIsConsistent(enrollment.getId());
        int changes = eventsOf(enrollment.getId()).size();
        runStepFor(d(NONPAYMENT_NET_YEAR, 6, 4));
        assertThat(eventsOf(enrollment.getId())).hasSize(changes);
    }

    @Test
    @Tag("D-185")
    @Tag("D-306")
    void payingTheDebtOfAnEnrollmentWithdrawnForNonpaymentNeverLiftsTheSuspensionWhenTheClosureEventWasLost() {
        arrange(NONPAYMENT_PAYMENT_YEAR);
        Invoice installment = overdueInstallmentDueOn(d(NONPAYMENT_PAYMENT_YEAR, 5, 10));
        evaluate(d(NONPAYMENT_PAYMENT_YEAR, 5, 20));
        evaluate(d(NONPAYMENT_PAYMENT_YEAR, 6, 1));
        assertThat(statusOf(enrollment.getId())).isEqualTo(EnrollmentPaymentStatus.SUSPENDED);
        jdbc.update("update course_enrollment set status = 'WITHDRAWN', payment_status = 'WITHDRAWN_FOR_NONPAYMENT' where id = ?", enrollment.getId());

        receiveMoney(installment, "100000.00", d(NONPAYMENT_PAYMENT_YEAR, 6, 2));
        allocateOn(installment, d(NONPAYMENT_PAYMENT_YEAR, 6, 2));
        awaitEventsDrained();

        assertThat(statusOf(enrollment.getId())).isEqualTo(EnrollmentPaymentStatus.WITHDRAWN_FOR_NONPAYMENT);
        assertThat(eventsOf(enrollment.getId())).doesNotContain("ACCOUNT_SETTLED");
        assertTheHistoryChainIsConsistent(enrollment.getId());
    }

    private String lastClosedMonthOf(Long enrollmentId) {
        return jdbc.queryForObject("select last_closed_month::text from enrollment_payment_standing where course_enrollment_id = ?", String.class, enrollmentId);
    }

    @Test
    @Tag("D-185")
    void anEnrollmentWithoutStandingHasNoHistory() {
        arrange(YEAR);

        assertThat(standingQuery.findStandingOfCourseEnrollment(enrollment.getId())).isEmpty();
        assertThat(standingQuery.listStatusChangesOfCourseEnrollment(enrollment.getId())).isEmpty();
        assertThat(standingQuery.findStandingOfProgramEnrollment(-1L)).isEmpty();
        assertThat(standingQuery.listStatusChangesOfProgramEnrollment(-1L)).isEmpty();
    }

    @Test
    @Tag("D-122")
    @Tag("D-185")
    void theReaderServesTheStandingAndItsHistoryOrRejectsAnEnrollmentWithoutStanding() {
        arrange(YEAR);
        assertThatThrownBy(() -> reader.readStanding(EnrollmentKind.COURSE, enrollment.getId())).isInstanceOf(EnrollmentPaymentStandingNotFoundException.class);
        assertThatThrownBy(() -> reader.readHistory(EnrollmentKind.COURSE, enrollment.getId())).isInstanceOf(EnrollmentPaymentStandingNotFoundException.class);
        overdueInstallmentDueOn(d(YEAR, 5, 10));
        evaluate(d(YEAR, 5, 20));

        assertThat(reader.readStanding(EnrollmentKind.COURSE, enrollment.getId()).status()).isEqualTo("OVERDUE");
        assertThat(reader.readStanding(EnrollmentKind.COURSE, enrollment.getId()).studentId()).isEqualTo(student.getId());
        assertThat(reader.readHistory(EnrollmentKind.COURSE, enrollment.getId())).extracting(EnrollmentPaymentStatusChangeResponse::event)
                .containsExactly("STANDING_OPENED", "OVERDUE_DEBT_DETECTED");
        assertThatThrownBy(() -> reader.readStanding(EnrollmentKind.PROGRAM, enrollment.getId() + 1_000_000)).isInstanceOf(EnrollmentPaymentStandingNotFoundException.class);
    }

    @Test
    @Tag("D-241")
    void aPaymentThatSettlesTheAccountAndTheMonthCloseRunningTogetherEndWithACoherentStanding() throws Exception {
        arrange(YEAR);
        Invoice installment = overdueInstallmentDueOn(d(YEAR, 5, 10));
        evaluate(d(YEAR, 5, 20));

        runInParallel(List.<Callable<Object>>of(
                () -> {
                    payInFullAndAwaitEvents(installment, d(YEAR, 5, 31), d(YEAR, 5, 31));
                    return null;
                },
                () -> {
                    evaluate(d(YEAR, 6, 1));
                    return null;
                }));

        assertThat(statusOf(enrollment.getId())).isEqualTo(EnrollmentPaymentStatus.UP_TO_DATE);
        assertThat(standingQuery.listStatusChangesOfCourseEnrollment(enrollment.getId())).extracting(EnrollmentPaymentStatusChangeView::toStatus)
                .doesNotContain(EnrollmentPaymentStatus.WITHDRAWN_FOR_NONPAYMENT);
        assertTheHistoryChainIsConsistent(enrollment.getId());
    }

    @Test
    @Tag("D-306")
    void concurrentEvaluationsWithRealTermsLeaveASingleConsistentChainOfChanges() throws Exception {
        arrange(YEAR);
        overdueInstallmentDueOn(d(YEAR, 5, 10));
        evaluate(d(YEAR, 5, 20));
        EnrollmentSuspensionRule tenDays = new EnrollmentSuspensionRule(1, 10);

        runInParallel(List.<Callable<Object>>of(
                () -> {
                    evaluate(d(YEAR, 6, 1), tenDays);
                    return null;
                },
                () -> {
                    evaluate(d(YEAR, 6, 1), tenDays);
                    return null;
                },
                () -> {
                    standingService.reevaluateExistingStanding(EnrollmentKind.COURSE, enrollment.getId(), d(YEAR, 6, 1));
                    return null;
                }));

        assertThat(statusOf(enrollment.getId())).isEqualTo(EnrollmentPaymentStatus.SUSPENDED);
        assertThat(eventsOf(enrollment.getId())).containsExactly("STANDING_OPENED", "OVERDUE_DEBT_DETECTED", "MONTH_CLOSED_WITH_UNPAID_INSTALLMENT");
        assertTheHistoryChainIsConsistent(enrollment.getId());
    }

    @Test
    @Tag("D-490")
    @Tag("D-473")
    void aReceiptSubmittedAfterTheCutoffOverAnInstallmentThatWasAlreadyOverdueAtTheCutoffCountsAsUnpaidAtTheCutoff() {
        arrange(YEAR);
        Invoice installment = overdueInstallmentDueOn(d(YEAR, 5, 10));
        evaluate(d(YEAR, 5, 20));

        MutableClock.INSTANCE.setDate(d(YEAR, 6, 2));
        jdbc.update("update invoice set payment_status = 'PAYMENT_UNDER_REVIEW' where id = ?", installment.getId());
        evaluate(d(YEAR, 6, 2));

        assertThat(eventsOf(enrollment.getId())).contains("MONTH_CLOSED_WITH_UNPAID_INSTALLMENT");
    }

    @Test
    @Tag("D-490")
    @Tag("D-473")
    void anInvoiceCancelledAfterTheCutoffCountsAsItWasAtTheCutoff() {
        arrange(YEAR);
        Invoice installment = overdueInstallmentDueOn(d(YEAR, 5, 10));
        evaluate(d(YEAR, 5, 20));

        MutableClock.INSTANCE.setDate(d(YEAR, 6, 2));
        jdbc.update("update invoice set payment_status = 'CANCELLED', paid_amount = 0, outstanding_amount = 0, credited_amount = total where id = ?", installment.getId());
        evaluate(d(YEAR, 6, 2));

        assertThat(eventsOf(enrollment.getId())).contains("MONTH_CLOSED_WITH_UNPAID_INSTALLMENT");
    }

    @Test
    @Tag("D-490")
    @Tag("D-473")
    void aChargebackBetweenTheCutoffAndTheFirstOfTheMonthRunDoesNotSuspendWhoPaidOnTime() {
        arrange(YEAR);
        Invoice installment = overdueInstallmentDueOn(d(YEAR, 5, 10));
        evaluate(d(YEAR, 5, 20));
        CurrentAccountEntry credit = receiveMoney(installment, "100000.00", d(YEAR, 5, 28));
        allocateOn(installment, d(YEAR, 5, 28));
        awaitEventsDrained();

        MutableClock.INSTANCE.setDate(d(YEAR, 6, 1));
        reversePayment(installment, credit, d(YEAR, 6, 1), 0);
        awaitEventsDrained();
        evaluate(d(YEAR, 6, 1));

        assertThat(eventsOf(enrollment.getId())).doesNotContain("MONTH_CLOSED_WITH_UNPAID_INSTALLMENT");
        assertThat(statusOf(enrollment.getId())).isNotEqualTo(EnrollmentPaymentStatus.SUSPENDED);
    }

    @Test
    @Tag("D-490")
    @Tag("D-154")
    @Tag("D-153")
    void aReceiptUploadedOnTimeProtectsTheInstallmentAtTheCutoffSoNobodyIsSuspendedAndNoSpuriousEventIsRecorded() {
        arrange(YEAR);
        Invoice installment = pendingInstallmentDueOn(d(YEAR, 5, 10));
        submitReceipt(installment, d(YEAR, 5, 10), true);
        evaluate(d(YEAR, 5, 20));

        evaluate(d(YEAR, 6, 1));

        assertThat(statusOf(enrollment.getId())).isEqualTo(EnrollmentPaymentStatus.UP_TO_DATE);
        assertThat(eventsOf(enrollment.getId())).containsExactly("STANDING_OPENED");
        assertTheHistoryChainIsConsistent(enrollment.getId());
    }

    @Test
    @Tag("D-490")
    @Tag("D-154")
    void aReceiptUploadedAfterTheDueDateDoesNotProtectTheInstallmentAndTheStudentStaysSuspendedUntilItIsResolved() {
        arrange(YEAR);
        Invoice installment = overdueInstallmentDueOn(d(YEAR, 5, 10));
        submitReceipt(installment, d(YEAR, 5, 11), false);
        evaluate(d(YEAR, 5, 20));

        evaluate(d(YEAR, 6, 1));

        assertThat(statusOf(enrollment.getId())).isEqualTo(EnrollmentPaymentStatus.SUSPENDED);
        assertThat(eventsOf(enrollment.getId())).contains("MONTH_CLOSED_WITH_UNPAID_INSTALLMENT");
    }

    @Test
    @Tag("D-490")
    @Tag("D-154")
    void aReceiptThatWasAlreadyRejectedBeforeTheCutoffNoLongerProtectsTheInstallment() {
        arrange(YEAR);
        Invoice installment = overdueInstallmentDueOn(d(YEAR, 5, 10));
        Long paymentId = submitReceipt(installment, d(YEAR, 5, 9), true);
        changePaymentStatus(paymentId, "BANK_TRANSFER_REJECTED", "REJECTED", d(YEAR, 5, 25));
        jdbc.update("update invoice set payment_status = 'OVERDUE' where id = ?", installment.getId());
        evaluate(d(YEAR, 5, 26));

        evaluate(d(YEAR, 6, 1));

        assertThat(statusOf(enrollment.getId())).isEqualTo(EnrollmentPaymentStatus.SUSPENDED);
    }

    @Test
    @Tag("D-490")
    @Tag("D-154")
    void aReceiptRejectedAfterTheCutoffStillProtectedTheInstallmentAtTheCutoff() {
        arrange(YEAR);
        Invoice installment = overdueInstallmentDueOn(d(YEAR, 5, 10));
        Long paymentId = submitReceipt(installment, d(YEAR, 5, 9), true);
        changePaymentStatus(paymentId, "BANK_TRANSFER_REJECTED", "REJECTED", d(YEAR, 6, 1));
        evaluate(d(YEAR, 5, 26));

        evaluate(d(YEAR, 6, 1));

        assertThat(eventsOf(enrollment.getId())).doesNotContain("MONTH_CLOSED_WITH_UNPAID_INSTALLMENT");
        assertThat(statusOf(enrollment.getId())).isNotEqualTo(EnrollmentPaymentStatus.SUSPENDED);
    }

    @Test
    @Tag("D-490")
    @Tag("D-153")
    void aReceiptProtectsOnlyTheInstallmentItWasMarkedFor() {
        arrange(YEAR);
        Invoice protectedInstallment = pendingInstallmentDueOn(d(YEAR, 4, 10));
        Invoice unprotectedInstallment = overdueInstallmentDueOn(d(YEAR, 5, 10));
        submitReceipt(protectedInstallment, d(YEAR, 4, 9), true);
        submitReceipt(unprotectedInstallment, d(YEAR, 5, 9), false);
        evaluate(d(YEAR, 5, 20));

        evaluate(d(YEAR, 6, 1));

        assertThat(statusOf(enrollment.getId())).isEqualTo(EnrollmentPaymentStatus.SUSPENDED);
    }

    @Test
    @Tag("D-185")
    void payingOnlyOneOfTwoOverdueInstallmentsKeepsTheEnrollmentSuspendedAndPayingTheSecondLiftsIt() {
        arrange(YEAR);
        Invoice first = overdueInstallmentDueOn(d(YEAR, 4, 10));
        Invoice second = overdueInstallmentDueOn(d(YEAR, 5, 10));
        evaluate(d(YEAR, 5, 20));
        evaluate(d(YEAR, 6, 1));
        assertThat(statusOf(enrollment.getId())).isEqualTo(EnrollmentPaymentStatus.SUSPENDED);

        payInFullAndAwaitEvents(first, d(YEAR, 6, 3), d(YEAR, 6, 3));
        assertThat(statusOf(enrollment.getId())).isEqualTo(EnrollmentPaymentStatus.SUSPENDED);

        payInFullAndAwaitEvents(second, d(YEAR, 6, 4), d(YEAR, 6, 4));
        assertThat(statusOf(enrollment.getId())).isEqualTo(EnrollmentPaymentStatus.UP_TO_DATE);
    }

    @Test
    @Tag("D-185")
    void aPartialPaymentDoesNotSettleTheInstallmentSoTheEnrollmentStaysSuspended() {
        arrange(YEAR);
        Invoice installment = overdueInstallmentDueOn(d(YEAR, 5, 10));
        evaluate(d(YEAR, 5, 20));
        evaluate(d(YEAR, 6, 1));

        receiveMoney(installment, "40000.00", d(YEAR, 6, 3));
        allocateOn(installment, d(YEAR, 6, 3));
        awaitEventsDrained();

        assertThat(jdbc.queryForObject("select payment_status from invoice where id = ?", String.class, installment.getId())).isEqualTo("OVERDUE");
        assertThat(statusOf(enrollment.getId())).isEqualTo(EnrollmentPaymentStatus.SUSPENDED);
    }

    @Test
    @Tag("D-185")
    void aCreditNoteWhoseFreeCreditSettlesTheOverdueInstallmentPublishesThePaidEventAndLiftsTheSuspension() {
        arrange(YEAR);
        Invoice paidInstallment = pendingInstallmentDueOn(d(YEAR, 4, 10));
        payInFullAndAwaitEvents(paidInstallment, d(YEAR, 4, 5), d(YEAR, 4, 5));
        Invoice overdueInstallment = overdueInstallmentDueOn(d(YEAR, 5, 10));
        evaluate(d(YEAR, 5, 20));
        evaluate(d(YEAR, 6, 1));
        assertThat(statusOf(enrollment.getId())).isEqualTo(EnrollmentPaymentStatus.SUSPENDED);

        MutableClock.INSTANCE.setDate(d(YEAR, 6, 3));
        pipeline.runOperation(creditNoteOperation, new IssueCreditNoteCommand(paidInstallment.getId(), CreditNoteReason.BILLING_ERROR, "Error",
                List.of(new CreditNoteLineAmount(1, ars(100000))), CreditNoteDestination.FREE_CREDIT, person.getId(), true, null));
        awaitEventsDrained();

        assertThat(jdbc.queryForObject("select payment_status from invoice where id = ?", String.class, overdueInstallment.getId())).isEqualTo("PAID");
        assertThat(statusOf(enrollment.getId())).isEqualTo(EnrollmentPaymentStatus.UP_TO_DATE);
        assertThat(eventsOf(enrollment.getId())).contains("ACCOUNT_SETTLED");
    }

    @Test
    @Tag("D-185")
    void aPaymentWhoseStandingReevaluationFailedLiftsTheSuspensionWhenThePendingPublicationIsReplayed() {
        arrange(YEAR);
        Invoice installment = overdueInstallmentDueOn(d(YEAR, 5, 10));
        evaluate(d(YEAR, 5, 20));
        evaluate(d(YEAR, 6, 1));
        Long standingId = jdbc.queryForObject("select id from enrollment_payment_standing where course_enrollment_id = ?", Long.class, enrollment.getId());
        jdbc.execute("create or replace function fail_standing_history_for_test() returns trigger language plpgsql as $$ begin raise exception 'forced failure'; end $$");
        jdbc.execute("create trigger trg_fail_standing_history_for_test before insert on enrollment_payment_status_change for each row "
                + "when (new.standing_id = " + standingId + ") execute function fail_standing_history_for_test()");
        try {
            payInFull(installment, d(YEAR, 6, 5), d(YEAR, 6, 5));
            await().atMost(Duration.ofSeconds(25)).until(() -> pendingStandingPublications() == 1L);
        } finally {
            jdbc.execute("drop trigger trg_fail_standing_history_for_test on enrollment_payment_status_change");
        }
        assertThat(jdbc.queryForObject("select payment_status from invoice where id = ?", String.class, installment.getId())).isEqualTo("PAID");
        assertThat(statusOf(enrollment.getId())).isEqualTo(EnrollmentPaymentStatus.SUSPENDED);

        incompleteEventPublications.resubmitIncompletePublications(publication -> publication.getEvent() instanceof InvoicePaidDomainEvent paid && paid.invoiceId().equals(installment.getId()));

        await().atMost(Duration.ofSeconds(25)).until(() -> statusOf(enrollment.getId()) == EnrollmentPaymentStatus.UP_TO_DATE);
        awaitEventsDrained();
        assertThat(pendingStandingPublications()).isZero();
    }

    private void cancelInstallment(Invoice installment, LocalDate on) {
        MutableClock.INSTANCE.setDate(on);
        pipeline.runOperation(cancelInvoiceOperation, CancelInvoiceCommand.byPerson(installment.getId(), CreditNoteReason.BILLING_ERROR, "Cancelación", person.getId(), true));
        awaitEventsDrained();
    }

    @Test
    @Tag("D-185")
    void cancellingTheOnlyOverdueInstallmentLiftsTheSuspensionRightAway() {
        arrange(YEAR);
        Invoice installment = overdueInstallmentDueOn(d(YEAR, 5, 10));
        evaluate(d(YEAR, 5, 20));
        evaluate(d(YEAR, 6, 1));
        assertThat(statusOf(enrollment.getId())).isEqualTo(EnrollmentPaymentStatus.SUSPENDED);

        cancelInstallment(installment, d(YEAR, 6, 3));

        assertThat(jdbc.queryForObject("select payment_status from invoice where id = ?", String.class, installment.getId())).isEqualTo("CANCELLED");
        assertThat(statusOf(enrollment.getId())).isEqualTo(EnrollmentPaymentStatus.UP_TO_DATE);
        assertThat(eventsOf(enrollment.getId())).contains("ACCOUNT_SETTLED");
        assertTheHistoryChainIsConsistent(enrollment.getId());
    }

    @Test
    @Tag("D-185")
    void cancellingOneOfTwoOverdueInstallmentsKeepsTheEnrollmentSuspended() {
        arrange(YEAR);
        Invoice first = overdueInstallmentDueOn(d(YEAR, 4, 10));
        overdueInstallmentDueOn(d(YEAR, 5, 10));
        evaluate(d(YEAR, 5, 20));
        evaluate(d(YEAR, 6, 1));
        assertThat(statusOf(enrollment.getId())).isEqualTo(EnrollmentPaymentStatus.SUSPENDED);

        cancelInstallment(first, d(YEAR, 6, 3));

        assertThat(statusOf(enrollment.getId())).isEqualTo(EnrollmentPaymentStatus.SUSPENDED);
        assertThat(eventsOf(enrollment.getId())).doesNotContain("ACCOUNT_SETTLED");
    }

    @Test
    @Tag("D-185")
    void cancellingAnInstallmentThatWasNotSuspendingAnyoneRecordsNoChange() {
        arrange(YEAR);
        Invoice upcoming = pendingInstallmentDueOn(d(YEAR, 7, 10));
        evaluate(d(YEAR, 5, 20));
        List<String> before = eventsOf(enrollment.getId());

        cancelInstallment(upcoming, d(YEAR, 6, 3));

        assertThat(eventsOf(enrollment.getId())).isEqualTo(before);
        assertThat(statusOf(enrollment.getId())).isEqualTo(EnrollmentPaymentStatus.UP_TO_DATE);
    }

    @Test
    @Tag("D-185")
    void aFailedReevaluationAfterACancellationLiftsTheSuspensionWhenThePendingPublicationIsReplayed() {
        arrange(YEAR);
        Invoice installment = overdueInstallmentDueOn(d(YEAR, 5, 10));
        evaluate(d(YEAR, 5, 20));
        evaluate(d(YEAR, 6, 1));
        Long standingId = jdbc.queryForObject("select id from enrollment_payment_standing where course_enrollment_id = ?", Long.class, enrollment.getId());
        jdbc.execute("create or replace function fail_standing_history_for_test() returns trigger language plpgsql as $$ begin raise exception 'forced failure'; end $$");
        jdbc.execute("create trigger trg_fail_standing_history_for_test before insert on enrollment_payment_status_change for each row "
                + "when (new.standing_id = " + standingId + ") execute function fail_standing_history_for_test()");
        try {
            MutableClock.INSTANCE.setDate(d(YEAR, 6, 3));
            pipeline.runOperation(cancelInvoiceOperation, CancelInvoiceCommand.byPerson(installment.getId(), CreditNoteReason.BILLING_ERROR, "Cancelación", person.getId(), true));
            await().atMost(Duration.ofSeconds(25)).until(() -> pendingStandingPublications() == 1L);
        } finally {
            jdbc.execute("drop trigger trg_fail_standing_history_for_test on enrollment_payment_status_change");
        }
        assertThat(jdbc.queryForObject("select payment_status from invoice where id = ?", String.class, installment.getId())).isEqualTo("CANCELLED");
        assertThat(statusOf(enrollment.getId())).isEqualTo(EnrollmentPaymentStatus.SUSPENDED);

        incompleteEventPublications.resubmitIncompletePublications(publication -> publication.getEvent() instanceof InvoiceCancelledEvent cancelled
                && cancelled.invoiceId().equals(installment.getId()));

        await().atMost(Duration.ofSeconds(25)).until(() -> statusOf(enrollment.getId()) == EnrollmentPaymentStatus.UP_TO_DATE);
        awaitEventsDrained();
        assertThat(pendingStandingPublications()).isZero();
    }
}
