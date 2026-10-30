package com.red_escuela.backend.billing.run.usecase;

import com.red_escuela.backend.billing.run.domain.MonthlyInvoiceRun;
import com.red_escuela.backend.billing.run.domain.MonthlyInvoiceRunTrigger;
import com.red_escuela.backend.billing.run.dto.MonthlyInvoiceRunResponse;
import com.red_escuela.backend.billing.run.exception.ManualIssuanceOutsideCurrentMonthException;
import com.red_escuela.backend.billing.run.exception.ManualIssuanceRequiresSimulationException;
import com.red_escuela.backend.billing.run.exception.MonthlyInvoiceRunAlreadyExistsException;
import com.red_escuela.backend.billing.run.exception.NightlyBillingNotCompletedException;
import com.red_escuela.backend.billing.run.policy.ManualIssuanceRequiresSimulationContext;
import com.red_escuela.backend.billing.run.policy.ManualIssuanceWithinCurrentMonthContext;
import com.red_escuela.backend.billing.run.repository.MonthlyInvoiceRunRepository;
import com.red_escuela.backend.billing.run.service.MonthlyInvoiceRunExecutor;
import com.red_escuela.backend.shared.UseCase;
import com.red_escuela.backend.shared.domain.policy.PolicySet;

import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;

@UseCase
public class ManualMonthlyInvoiceIssuanceService implements ManualMonthlyInvoiceIssuanceUseCase {
    private final MonthlyInvoiceRunExecutor executor;
    private final MonthlyInvoiceRunRepository runRepository;
    private final MonthlyInvoiceRunQueryUseCase queryUseCase;
    private final PolicySet<ManualIssuanceWithinCurrentMonthContext> withinCurrentMonthPolicySet;
    private final PolicySet<ManualIssuanceRequiresSimulationContext> requiresSimulationPolicySet;
    private final Clock clock;

    public ManualMonthlyInvoiceIssuanceService(MonthlyInvoiceRunExecutor executor, MonthlyInvoiceRunRepository runRepository, MonthlyInvoiceRunQueryUseCase queryUseCase,
                                               PolicySet<ManualIssuanceWithinCurrentMonthContext> manualIssuanceWithinCurrentMonthPolicySet,
                                               PolicySet<ManualIssuanceRequiresSimulationContext> manualIssuanceRequiresSimulationPolicySet, Clock clock) {
        this.executor = executor;
        this.runRepository = runRepository;
        this.queryUseCase = queryUseCase;
        this.withinCurrentMonthPolicySet = manualIssuanceWithinCurrentMonthPolicySet;
        this.requiresSimulationPolicySet = manualIssuanceRequiresSimulationPolicySet;
        this.clock = clock;
    }

    @Override
    public MonthlyInvoiceRunResponse issueMonthlyInvoicesManuallyAsOfToday(YearMonth period, Long simulationRunId) {
        LocalDate today = LocalDate.now(clock);
        withinCurrentMonthPolicySet.evaluateAllRules(new ManualIssuanceWithinCurrentMonthContext(period, YearMonth.from(today))).firstDenial().ifPresent(denial -> {
            throw new ManualIssuanceOutsideCurrentMonthException();
        });
        MonthlyInvoiceRun simulation = runRepository.findById(simulationRunId).orElse(null);
        requiresSimulationPolicySet.evaluateAllRules(new ManualIssuanceRequiresSimulationContext(period, today, simulation)).firstDenial().ifPresent(denial -> {
            throw new ManualIssuanceRequiresSimulationException();
        });
        executor.findNightlyBillingDenialOf(today).ifPresent(denial -> {
            throw new NightlyBillingNotCompletedException();
        });
        MonthlyInvoiceRun run = executor.runMonthlyInvoicesOfPeriodPricedAt(period, today, period.atDay(1), MonthlyInvoiceRunTrigger.MANUAL)
                .orElseThrow(() -> new MonthlyInvoiceRunAlreadyExistsException(period));
        return queryUseCase.findMonthlyInvoiceRun(run.getId());
    }
}
