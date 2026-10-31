package com.red_escuela.backend.billing.service;

import com.red_escuela.backend.billing.entity.CohortIntake;
import com.red_escuela.backend.billing.entity.PaymentPlanCadence;
import com.red_escuela.backend.billing.invoice.domain.FeeType;
import com.red_escuela.backend.billing.run.domain.MonthlyInvoiceRun;
import com.red_escuela.backend.billing.run.domain.MonthlyInvoiceRunMode;
import com.red_escuela.backend.billing.run.domain.MonthlyInvoiceRunStatus;
import com.red_escuela.backend.billing.run.domain.MonthlyInvoiceRunTrigger;
import com.red_escuela.backend.billing.run.dto.MonthlyInvoiceRunResponse;
import com.red_escuela.backend.billing.run.exception.ManualIssuanceOutsideCurrentMonthException;
import com.red_escuela.backend.billing.run.exception.ManualIssuanceRequiresSimulationException;
import com.red_escuela.backend.billing.run.exception.MonthlyInvoiceRunAlreadyExistsException;
import com.red_escuela.backend.billing.run.exception.NightlyBillingNotCompletedException;
import com.red_escuela.backend.billing.run.repository.MonthlyInvoiceRunRepository;
import com.red_escuela.backend.billing.run.service.MonthlyInvoiceRunExecutor;
import com.red_escuela.backend.billing.run.usecase.ManualMonthlyInvoiceIssuanceUseCase;
import com.red_escuela.backend.billing.run.usecase.MonthlyInvoiceSimulationUseCase;
import com.red_escuela.backend.shared.exception.ErrorCodeResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("D-055")
@Tag("D-161")
@Tag("D-454")
class ManualMonthlyInvoiceIssuanceTest extends AbstractInvoiceSequenceTest {
    private static final YearMonth APRIL = YearMonth.of(2026, 4);
    private static final BigDecimal INCREASED_INSTALLMENT = new BigDecimal("99999");

    @Autowired
    private ManualMonthlyInvoiceIssuanceUseCase manualIssuance;
    @Autowired
    private MonthlyInvoiceSimulationUseCase simulationUseCase;
    @Autowired
    private com.red_escuela.backend.billing.service.MonthlyInstallmentRowProcessor rowProcessor;
    @Autowired
    private MonthlyInvoiceRunExecutor executor;
    @Autowired
    private MonthlyInvoiceRunRepository runRepository;
    @Autowired
    private com.red_escuela.backend.billing.run.service.MonthlyInvoiceRunItemProcessor itemProcessor;

    @BeforeEach
    void forgetPreviousRuns() {
        jdbcTemplate.update("delete from monthly_invoice_simulation_alert");
        jdbcTemplate.update("delete from monthly_invoice_simulation_line");
        jdbcTemplate.update("delete from monthly_invoice_run_item_attempt");
        jdbcTemplate.update("delete from monthly_invoice_run_item");
        jdbcTemplate.update("delete from monthly_invoice_run");
    }

    private void forgetTheNightOf(LocalDate day) {
        String nights = "select id from nightly_billing_run where business_date = ?";
        jdbcTemplate.update("delete from billing_run_item where billing_run_id in (select id from billing_run where nightly_billing_run_id in (" + nights + "))", day);
        jdbcTemplate.update("delete from billing_run where nightly_billing_run_id in (" + nights + ")", day);
        jdbcTemplate.update("delete from nightly_billing_run where business_date = ?", day);
    }

    private Career careerWithAnIncreaseFromTheSecond() {
        Career career = newCareer();
        newPrice(career, FeeType.MONTHLY_INSTALLMENT, INCREASED_INSTALLMENT.longValue(), date(2026, 4, 2));
        return career;
    }

    private Long cohortOf(Career career) {
        Year year = newYear(career, 2026, 10, 1, 2, null);
        return newCohort(year, CohortIntake.FIRST, date(2026, 3, 1));
    }

    private Long enrollInCohort(Career career, Long cohort) {
        today(date(2026, 3, 25));
        return newEnrollment(career, cohort, PaymentPlanCadence.MONTHLY, APRIL, date(2026, 3, 10));
    }

    private Long enrollmentOfAprilCohort(Career career) {
        return enrollInCohort(career, cohortOf(career));
    }

    private Long simulateOnTheThird() {
        today(date(2026, 4, 3));
        return simulationUseCase.simulateMonthlyInvoicesOfPeriodAsOfToday(APRIL).runId();
    }

    private long monthlyInstallmentsOf(Long enrollment) {
        return Collections.frequency(periods(enrollment, FeeType.MONTHLY_INSTALLMENT), APRIL);
    }

    @Test
    void aDirectorWithTodaysSimulationIssuesTheMonthWithTheFirstDayPriceAndTheManualTrigger() {
        Long enrollment = enrollmentOfAprilCohort(careerWithAnIncreaseFromTheSecond());
        Long simulationId = simulateOnTheThird();
        job.completeTheNightOf(date(2026, 4, 3));

        MonthlyInvoiceRunResponse response = manualIssuance.issueMonthlyInvoicesManuallyAsOfToday(APRIL, simulationId);

        assertThat(response.mode()).isEqualTo(MonthlyInvoiceRunMode.REAL);
        assertThat(response.trigger()).isEqualTo(MonthlyInvoiceRunTrigger.MANUAL);
        assertThat(response.status()).isEqualTo(MonthlyInvoiceRunStatus.COMPLETED);
        assertThat(response.period()).isEqualTo(date(2026, 4, 1));
        assertThat(response.businessDate()).isEqualTo(date(2026, 4, 3));
        assertThat(response.pricingDate()).isEqualTo(date(2026, 4, 1));
        assertThat(monthlyInstallmentsOf(enrollment)).isEqualTo(1);
        invoices(enrollment, FeeType.MONTHLY_INSTALLMENT).forEach(invoice ->
                assertThat(amountDue(invoice).amount()).isLessThan(INCREASED_INSTALLMENT));
    }

    @Test
    void withoutAnySimulationTheManualIssuanceIsRejectedAndNothingIsIssued() {
        Long enrollment = enrollmentOfAprilCohort(careerWithAnIncreaseFromTheSecond());
        today(date(2026, 4, 3));
        job.completeTheNightOf(date(2026, 4, 3));

        assertThatThrownBy(() -> manualIssuance.issueMonthlyInvoicesManuallyAsOfToday(APRIL, 987654321L)).isInstanceOf(ManualIssuanceRequiresSimulationException.class);

        assertThat(runRepository.findByPeriodOrderByIdAsc(APRIL.atDay(1))).isEmpty();
        assertThat(invoices(enrollment, FeeType.MONTHLY_INSTALLMENT)).isEmpty();
    }

    @Test
    void aSimulationFromAnEarlierDayDoesNotAuthorizeTheManualIssuance() {
        enrollmentOfAprilCohort(careerWithAnIncreaseFromTheSecond());
        today(date(2026, 4, 2));
        Long yesterdaysSimulation = simulationUseCase.simulateMonthlyInvoicesOfPeriodAsOfToday(APRIL).runId();
        today(date(2026, 4, 3));
        job.completeTheNightOf(date(2026, 4, 3));

        assertThatThrownBy(() -> manualIssuance.issueMonthlyInvoicesManuallyAsOfToday(APRIL, yesterdaysSimulation)).isInstanceOf(ManualIssuanceRequiresSimulationException.class);
    }

    @Test
    void aSimulationReplacedByANewerOneOfTheSameDayStopsAuthorizingWithItsOldId() {
        enrollmentOfAprilCohort(careerWithAnIncreaseFromTheSecond());
        Long replacedSimulation = simulateOnTheThird();
        Long currentSimulation = simulationUseCase.simulateMonthlyInvoicesOfPeriodAsOfToday(APRIL).runId();
        job.completeTheNightOf(date(2026, 4, 3));

        assertThatThrownBy(() -> manualIssuance.issueMonthlyInvoicesManuallyAsOfToday(APRIL, replacedSimulation)).isInstanceOf(ManualIssuanceRequiresSimulationException.class);
        assertThat(manualIssuance.issueMonthlyInvoicesManuallyAsOfToday(APRIL, currentSimulation).status()).isEqualTo(MonthlyInvoiceRunStatus.COMPLETED);
    }

    @Test
    void aRequestForAnotherMonthIsRejectedEvenWithTodaysSimulation() {
        enrollmentOfAprilCohort(careerWithAnIncreaseFromTheSecond());
        Long simulationId = simulateOnTheThird();
        job.completeTheNightOf(date(2026, 4, 3));

        assertThatThrownBy(() -> manualIssuance.issueMonthlyInvoicesManuallyAsOfToday(APRIL.minusMonths(1), simulationId)).isInstanceOf(ManualIssuanceOutsideCurrentMonthException.class);
        assertThatThrownBy(() -> manualIssuance.issueMonthlyInvoicesManuallyAsOfToday(APRIL.plusMonths(1), simulationId)).isInstanceOf(ManualIssuanceOutsideCurrentMonthException.class);
    }

    @Test
    void withoutACompleteNightTheManualIssuanceIsRejectedAndNoRunIsRecorded() {
        enrollmentOfAprilCohort(careerWithAnIncreaseFromTheSecond());
        Long simulationId = simulateOnTheThird();
        forgetTheNightOf(date(2026, 4, 3));

        assertThatThrownBy(() -> manualIssuance.issueMonthlyInvoicesManuallyAsOfToday(APRIL, simulationId)).isInstanceOf(NightlyBillingNotCompletedException.class);

        assertThat(runRepository.findByPeriodOrderByIdAsc(APRIL.atDay(1)).stream().filter(run -> run.getMode() == MonthlyInvoiceRunMode.REAL)).isEmpty();
    }

    @Test
    void aRealRunBlockedByTheNightOnDayOneDoesNotPreventTheManualRecovery() {
        Long enrollment = enrollmentOfAprilCohort(careerWithAnIncreaseFromTheSecond());
        today(date(2026, 4, 1));
        forgetTheNightOf(date(2026, 4, 1));
        MonthlyInvoiceRun blocked = executor.runMonthlyInvoicesOfPeriod(APRIL, date(2026, 4, 1), MonthlyInvoiceRunTrigger.SCHEDULED).orElseThrow();
        assertThat(blocked.getStatus()).isEqualTo(MonthlyInvoiceRunStatus.FAILED);
        Long simulationId = simulateOnTheThird();
        job.completeTheNightOf(date(2026, 4, 3));

        MonthlyInvoiceRunResponse response = manualIssuance.issueMonthlyInvoicesManuallyAsOfToday(APRIL, simulationId);

        assertThat(response.status()).isEqualTo(MonthlyInvoiceRunStatus.COMPLETED);
        assertThat(monthlyInstallmentsOf(enrollment)).isEqualTo(1);
    }

    private Long enrollmentWithFiveDueDays() {
        Career career = careerWithAnIncreaseFromTheSecond();
        Year year = newYear(career, 2026, 10, 1, 2, null);
        configureYear(year, template -> com.red_escuela.backend.billing.planning.TestTemplateTerms.setRecurringFeeDueDays(template, 5));
        return enrollInCohort(career, newCohort(year, CohortIntake.FIRST, date(2026, 3, 1)));
    }

    @Test
    @Tag("D-509")
    void aManualIssuanceLongAfterTheDueDaysFromTheFirstDayHavePassedKeepsEveryDueDateOnOrAfterTheIssueDate() {
        Long enrollment = enrollmentWithFiveDueDays();
        today(date(2026, 4, 10));
        Long simulationId = simulationUseCase.simulateMonthlyInvoicesOfPeriodAsOfToday(APRIL).runId();
        job.completeTheNightOf(date(2026, 4, 10));

        MonthlyInvoiceRunResponse response = manualIssuance.issueMonthlyInvoicesManuallyAsOfToday(APRIL, simulationId);

        assertThat(response.status()).isEqualTo(MonthlyInvoiceRunStatus.COMPLETED);
        assertThat(monthlyInstallmentsOf(enrollment)).isEqualTo(1);
        invoices(enrollment, FeeType.MONTHLY_INSTALLMENT).stream().filter(invoice -> APRIL.equals(invoice.getPeriod())).forEach(invoice -> {
            assertThat(invoice.getIssueDate()).isEqualTo(date(2026, 4, 10));
            assertThat(invoice.getDueDate()).isEqualTo(date(2026, 4, 15));
        });
    }

    @Test
    void theSimulationOfALateDayWouldIssueTheSameInstallmentTheManualIssuanceThenIssues() {
        Long enrollment = enrollmentWithFiveDueDays();
        today(date(2026, 4, 10));

        var simulation = simulationUseCase.simulateMonthlyInvoicesOfPeriodAsOfToday(APRIL);

        assertThat(simulation.lines()).filteredOn(line -> line.enrollmentId().equals(enrollment))
                .singleElement().satisfies(line -> assertThat(line.result()).isEqualTo(com.red_escuela.backend.billing.run.domain.MonthlyInvoiceRunItemResult.ISSUED));
    }

    @Test
    @Tag("D-509")
    void aFailedItemRetriedAfterTheDueDaysFromTheFirstDayHavePassedIssuesWithTheDueDateCountedFromTheRetryDay() {
        Long enrollment = enrollmentWithFiveDueDays();
        today(date(2026, 4, 12));

        var outcome = itemProcessor.attemptEnrollmentMonthIssuance(com.red_escuela.backend.billing.run.domain.MonthlyInvoiceEnrollmentType.PROGRAM, enrollment, APRIL, date(2026, 4, 1), com.red_escuela.backend.billing.invoice.domain.issuing.InvoiceIssuanceDestination.EMIT_NOW);

        assertThat(outcome.result()).isEqualTo(com.red_escuela.backend.billing.run.domain.MonthlyInvoiceRunItemResult.ISSUED);
        invoices(enrollment, FeeType.MONTHLY_INSTALLMENT).stream().filter(invoice -> APRIL.equals(invoice.getPeriod())).forEach(invoice -> {
            assertThat(invoice.getIssueDate()).isEqualTo(date(2026, 4, 12));
            assertThat(invoice.getDueDate()).isEqualTo(date(2026, 4, 17));
        });
    }

    private LocalDate previewedInstallmentDueDate(Long enrollment, LocalDate issueDate) {
        return rowProcessor.previewProgramEnrollmentIssuance(enrollment, APRIL, date(2026, 4, 1), issueDate).wouldBeIssued().stream()
                .filter(charge -> charge.kind() == FeeType.MONTHLY_INSTALLMENT).findFirst().orElseThrow().dueDate();
    }

    @Test
    @Tag("D-511")
    void aPreviewWithTheNextPeriodFirstDayAsIssueDateOnTheTwentiethOfTheMonthBeforeShowsTheFirstDayPlusTheDueDaysNotTodayPlusTheDueDays() {
        Long enrollment = enrollmentWithFiveDueDays();
        today(date(2026, 3, 20));

        assertThat(previewedInstallmentDueDate(enrollment, date(2026, 4, 1))).isEqualTo(date(2026, 4, 6));
    }

    @Test
    @Tag("D-511")
    void thePreviewOfTheManualIssuanceDayShowsTheSameDueDateTheManualIssuanceThenEmits() {
        Long enrollment = enrollmentWithFiveDueDays();
        today(date(2026, 4, 10));
        Long simulationId = simulationUseCase.simulateMonthlyInvoicesOfPeriodAsOfToday(APRIL).runId();
        LocalDate previewedDueDate = previewedInstallmentDueDate(enrollment, date(2026, 4, 10));
        job.completeTheNightOf(date(2026, 4, 10));

        manualIssuance.issueMonthlyInvoicesManuallyAsOfToday(APRIL, simulationId);

        invoices(enrollment, FeeType.MONTHLY_INSTALLMENT).stream().filter(invoice -> APRIL.equals(invoice.getPeriod()))
                .forEach(invoice -> assertThat(invoice.getDueDate()).isEqualTo(previewedDueDate));
    }

    @Test
    @Tag("D-511")
    void theNightlyRunOfTheFirstDayKeepsTheDueDateAtTheFirstDayPlusTheDueDays() {
        Long enrollment = enrollmentWithFiveDueDays();
        today(date(2026, 4, 1));
        job.completeTheNightOf(date(2026, 4, 1));

        MonthlyInvoiceRun run = executor.runMonthlyInvoicesOfPeriod(APRIL, date(2026, 4, 1), MonthlyInvoiceRunTrigger.SCHEDULED).orElseThrow();

        assertThat(run.getStatus()).isEqualTo(MonthlyInvoiceRunStatus.COMPLETED);
        invoices(enrollment, FeeType.MONTHLY_INSTALLMENT).stream().filter(invoice -> APRIL.equals(invoice.getPeriod())).forEach(invoice -> {
            assertThat(invoice.getIssueDate()).isEqualTo(date(2026, 4, 1));
            assertThat(invoice.getDueDate()).isEqualTo(date(2026, 4, 6));
        });
    }

    @Test
    void aSecondManualIssuanceOfTheSamePeriodIsRejectedWithoutIssuingAgain() {
        Long enrollment = enrollmentOfAprilCohort(careerWithAnIncreaseFromTheSecond());
        Long simulationId = simulateOnTheThird();
        job.completeTheNightOf(date(2026, 4, 3));
        manualIssuance.issueMonthlyInvoicesManuallyAsOfToday(APRIL, simulationId);

        assertThatThrownBy(() -> manualIssuance.issueMonthlyInvoicesManuallyAsOfToday(APRIL, simulationId)).isInstanceOf(MonthlyInvoiceRunAlreadyExistsException.class);

        assertThat(monthlyInstallmentsOf(enrollment)).isEqualTo(1);
        assertThat(runRepository.findByPeriodOrderByIdAsc(APRIL.atDay(1)).stream().filter(run -> run.getMode() == MonthlyInvoiceRunMode.REAL)).hasSize(1);
    }

    @Test
    void twoSimultaneousManualIssuancesLeaveOneRealRunAndOneInstallmentPerEnrollment() throws Exception {
        Career career = careerWithAnIncreaseFromTheSecond();
        Long cohort = cohortOf(career);
        List<Long> enrollments = List.of(enrollInCohort(career, cohort), enrollInCohort(career, cohort));
        Long simulationId = simulateOnTheThird();
        job.completeTheNightOf(date(2026, 4, 3));
        var pool = Executors.newFixedThreadPool(2);
        List<Future<MonthlyInvoiceRunResponse>> attempts = new ArrayList<>();
        try {
            Callable<MonthlyInvoiceRunResponse> attempt = () -> manualIssuance.issueMonthlyInvoicesManuallyAsOfToday(APRIL, simulationId);
            attempts.add(pool.submit(attempt));
            attempts.add(pool.submit(attempt));
            int succeeded = 0;
            int rejected = 0;
            for (Future<MonthlyInvoiceRunResponse> attempted : attempts) {
                try {
                    attempted.get();
                    succeeded++;
                } catch (ExecutionException e) {
                    assertThat(e.getCause()).isInstanceOf(MonthlyInvoiceRunAlreadyExistsException.class);
                    rejected++;
                }
            }
            assertThat(succeeded).isEqualTo(1);
            assertThat(rejected).isEqualTo(1);
        } finally {
            pool.shutdown();
        }

        assertThat(runRepository.findByPeriodOrderByIdAsc(APRIL.atDay(1)).stream().filter(run -> run.getMode() == MonthlyInvoiceRunMode.REAL)).hasSize(1);
        enrollments.forEach(enrollment -> assertThat(monthlyInstallmentsOf(enrollment)).isEqualTo(1));
    }

    @Test
    void everyRejectionReportsItsContractCode() {
        assertThat(ErrorCodeResponse.of(new ManualIssuanceRequiresSimulationException()).code()).isEqualTo("MANUAL_ISSUANCE_REQUIRES_SIMULATION");
        assertThat(ErrorCodeResponse.of(new ManualIssuanceOutsideCurrentMonthException()).code()).isEqualTo("MANUAL_ISSUANCE_OUTSIDE_CURRENT_MONTH");
        assertThat(ErrorCodeResponse.of(new NightlyBillingNotCompletedException()).code()).isEqualTo("NIGHTLY_BILLING_NOT_COMPLETED");
        assertThat(ErrorCodeResponse.of(new MonthlyInvoiceRunAlreadyExistsException(APRIL)).code()).isEqualTo("MONTHLY_INVOICE_RUN_ALREADY_EXISTS");
    }
}
