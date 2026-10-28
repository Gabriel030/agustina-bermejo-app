package com.red_escuela.backend.billing.collections.service;

import com.red_escuela.backend.academicstructure.entity.Course;
import com.red_escuela.backend.academicstructure.repository.CourseRepository;
import com.red_escuela.backend.billing.api.EnrollmentPaymentStandingQuery;
import com.red_escuela.backend.billing.api.EnrollmentPaymentStatusChangeView;
import com.red_escuela.backend.billing.collections.domain.EnrollmentKind;
import com.red_escuela.backend.billing.collections.dto.EnrollmentPaymentStatusChangeResponse;
import com.red_escuela.backend.billing.collections.exception.EnrollmentPaymentStandingNotFoundException;
import com.red_escuela.backend.billing.collections.domain.EnrollmentSuspensionRule;
import com.red_escuela.backend.billing.collections.operation.EnrollmentPaymentStandingStep;
import com.red_escuela.backend.billing.configuration.domain.LatePaymentTerms;
import com.red_escuela.backend.billing.entity.EnrollmentPaymentStatus;
import com.red_escuela.backend.billing.invoice.domain.FeeType;
import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.billing.support.BillingTestFixtures;
import com.red_escuela.backend.enrollment.entity.CourseEnrollment;
import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.enrollment.repository.CourseEnrollmentRepository;
import com.red_escuela.backend.shared.domain.StatusType;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EnrollmentPaymentStandingIT extends AbstractLateFeeScenarioIT {
    private static final int YEAR = 2027;
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
    private CourseRepository courseRepository;
    @Autowired
    private CourseEnrollmentRepository courseEnrollmentRepository;

    private void evaluate(LocalDate businessDate) {
        standingService.evaluateEnrollmentStanding(EnrollmentKind.COURSE, enrollment.getId(), businessDate, END_OF_MONTH);
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
    }

    @Test
    @Tag("D-241")
    @Tag("D-061")
    @Tag("D-292")
    void anUnpaidLateFeeAloneLeavesTheEnrollmentOverdueAtMonthCloseButNeverSuspended() {
        arrange(YEAR);
        Invoice paid = paidInstallmentDueOn(d(YEAR, 5, 10));
        overdueLateFeeOf(paid, d(YEAR, 5, 20));

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

        payInFull(installment, d(YEAR, 6, 5), d(YEAR, 6, 5));
        assertThat(statusOf(enrollment.getId())).isEqualTo(EnrollmentPaymentStatus.SUSPENDED);

        payInFull(lateFee, d(YEAR, 6, 6), d(YEAR, 6, 6));
        assertThat(statusOf(enrollment.getId())).isEqualTo(EnrollmentPaymentStatus.UP_TO_DATE);
        assertThat(standingQuery.findStandingOfCourseEnrollment(enrollment.getId()).orElseThrow().suspendedSince()).isNull();
        assertThat(eventsOf(enrollment.getId())).containsExactly("STANDING_OPENED", "OVERDUE_DEBT_DETECTED", "MONTH_CLOSED_WITH_UNPAID_INSTALLMENT", "ACCOUNT_SETTLED");
        List<EnrollmentPaymentStatusChangeView> history = standingQuery.listStatusChangesOfCourseEnrollment(enrollment.getId());
        assertThat(history.get(2).businessDate()).isEqualTo(d(YEAR, 6, 1));
        assertThat(history.get(2).actorProcess()).isEqualTo(EnrollmentPaymentStandingService.STANDING_PROCESS);
        assertThat(history.get(2).reason()).isNotBlank();
        assertThat(history.get(3).businessDate()).isEqualTo(d(YEAR, 6, 6));
    }

    @Test
    @Tag("D-241")
    @Tag("D-146")
    void anOverdueInvoiceThatIsNotAMonthlyInstallmentMakesTheEnrollmentOverdueButNeverSuspendsIt() {
        arrange(YEAR);
        BillingTestFixtures.invoice().forCourseEnrollment(enrollment).ofType(FeeType.ADMINISTRATIVE_FEE).forPeriod(YearMonth.of(YEAR, 5)).dueOn(d(YEAR, 5, 10))
                .withBaseAmount(ars(5000)).overdue().build();

        evaluate(d(YEAR, 6, 1));

        assertThat(statusOf(enrollment.getId())).isEqualTo(EnrollmentPaymentStatus.OVERDUE);
    }

    @Test
    @Tag("D-146")
    void aBalanceOwedForAnInstallmentThatIsNotDueYetKeepsTheEnrollmentUpToDate() {
        arrange(YEAR);
        pendingInstallmentDueOn(d(YEAR, 7, 10));

        evaluate(d(YEAR, 6, 1));

        assertThat(statusOf(enrollment.getId())).isEqualTo(EnrollmentPaymentStatus.UP_TO_DATE);
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

        evaluate(d(YEAR, 6, 1));
        standingService.evaluateEnrollmentStanding(EnrollmentKind.COURSE, other.getId(), d(YEAR, 6, 1), END_OF_MONTH);

        assertThat(statusOf(enrollment.getId())).isEqualTo(EnrollmentPaymentStatus.SUSPENDED);
        assertThat(statusOf(other.getId())).isEqualTo(EnrollmentPaymentStatus.UP_TO_DATE);
    }

    @Test
    @Tag("D-185")
    void theNightlyStepOpensTheStandingOfEveryActiveEnrollmentAndEvaluatesIt() {
        arrange(YEAR);
        overdueInstallmentDueOn(d(YEAR, 5, 10));

        EnrollmentPaymentStandingStep.Result result = standingStep.evaluateActiveEnrollments(d(YEAR, 6, 1), LatePaymentTerms.initial());

        assertThat(result.processed()).isPositive();
        assertThat(result.candidates()).isEqualTo(result.processed() + result.failed());
        assertThat(statusOf(enrollment.getId())).isEqualTo(EnrollmentPaymentStatus.SUSPENDED);
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
        List<Callable<Object>> tasks = List.of(
                () -> {
                    payInFull(installment, d(YEAR, 5, 31), d(YEAR, 5, 31));
                    return null;
                },
                () -> {
                    evaluate(d(YEAR, 6, 1));
                    return null;
                });
        ExecutorService executor = Executors.newFixedThreadPool(tasks.size());
        try {
            for (Future<Object> future : executor.invokeAll(tasks)) {
                future.get();
            }
        } finally {
            executor.shutdownNow();
        }

        assertThat(statusOf(enrollment.getId())).isEqualTo(EnrollmentPaymentStatus.UP_TO_DATE);
        assertThat(standingQuery.listStatusChangesOfCourseEnrollment(enrollment.getId())).extracting(EnrollmentPaymentStatusChangeView::toStatus)
                .doesNotContain(EnrollmentPaymentStatus.WITHDRAWN_FOR_NONPAYMENT);
    }
}
