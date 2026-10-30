package com.red_escuela.backend.billing.run.policy;

import com.red_escuela.backend.billing.run.domain.MonthlyInvoiceRun;
import com.red_escuela.backend.billing.run.domain.MonthlyInvoiceRunEvent;
import com.red_escuela.backend.billing.run.domain.MonthlyInvoiceRunTrigger;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("D-055")
@Tag("D-161")
class ManualIssuanceRequiresSimulationPolicyTest {
    private static final LocalDate TODAY = LocalDate.of(2031, 10, 3);
    private static final YearMonth PERIOD = YearMonth.of(2031, 10);
    private static final LocalDateTime AT = LocalDateTime.of(2031, 10, 3, 9, 0);
    private final ManualIssuanceRequiresSimulationPolicy policy = new ManualIssuanceRequiresSimulationPolicy();

    private static MonthlyInvoiceRun completedSimulation(YearMonth period, LocalDate simulatedOn) {
        MonthlyInvoiceRun run = MonthlyInvoiceRun.startSimulationRun(period.atDay(1), simulatedOn, period.atDay(1), MonthlyInvoiceRunTrigger.MANUAL, AT);
        run.finishMonthlyRun(MonthlyInvoiceRunEvent.RUN_FINISHED_CLEAN, AT);
        return run;
    }

    private PolicyVerdict evaluate(MonthlyInvoiceRun simulation) {
        return policy.evaluateRule(new ManualIssuanceRequiresSimulationContext(PERIOD, TODAY, simulation));
    }

    @Test
    void aCompletedSimulationOfThePeriodMadeTodayIsAllowed() {
        assertThat(evaluate(completedSimulation(PERIOD, TODAY)).allowed()).isTrue();
    }

    @Test
    void noSimulationIsDenied() {
        PolicyVerdict verdict = evaluate(null);

        assertThat(verdict.allowed()).isFalse();
        assertThat(verdict.code()).isEqualTo("MANUAL_ISSUANCE_REQUIRES_SIMULATION");
    }

    @Test
    void aSimulationMadeYesterdayIsDenied() {
        assertThat(evaluate(completedSimulation(PERIOD, TODAY.minusDays(1))).allowed()).isFalse();
    }

    @Test
    void aSimulationOfAnotherPeriodIsDenied() {
        assertThat(evaluate(completedSimulation(PERIOD.minusMonths(1), TODAY)).allowed()).isFalse();
    }

    @Test
    void aSimulationStillRunningIsDenied() {
        MonthlyInvoiceRun running = MonthlyInvoiceRun.startSimulationRun(PERIOD.atDay(1), TODAY, PERIOD.atDay(1), MonthlyInvoiceRunTrigger.MANUAL, AT);

        assertThat(evaluate(running).allowed()).isFalse();
    }

    @Test
    void aRealRunIsNotASimulation() {
        MonthlyInvoiceRun real = MonthlyInvoiceRun.startRealDirectRun(PERIOD.atDay(1), TODAY, MonthlyInvoiceRunTrigger.SCHEDULED, AT);
        real.finishMonthlyRun(MonthlyInvoiceRunEvent.RUN_FINISHED_CLEAN, AT);

        assertThat(evaluate(real).allowed()).isFalse();
    }
}
