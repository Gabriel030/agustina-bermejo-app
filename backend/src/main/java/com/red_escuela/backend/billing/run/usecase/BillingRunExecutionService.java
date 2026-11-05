package com.red_escuela.backend.billing.run.usecase;

import com.red_escuela.backend.billing.run.domain.NightlyBillingRun;
import com.red_escuela.backend.billing.run.domain.NightlyBillingTrigger;
import com.red_escuela.backend.billing.run.dto.NightlyBillingExecutionResponse;
import com.red_escuela.backend.billing.run.dto.NightlyBillingRunResponse;
import com.red_escuela.backend.billing.run.repository.NightlyBillingRunRepository;
import com.red_escuela.backend.billing.run.service.NightlyBillingRunner;
import com.red_escuela.backend.shared.UseCase;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Optional;

@UseCase
public class BillingRunExecutionService implements BillingRunExecutionUseCase {
    private final NightlyBillingRunner runner;
    private final NightlyBillingRunRepository nightlyRunRepository;
    private final BillingRunQueryUseCase queryUseCase;
    private final Clock clock;

    public BillingRunExecutionService(NightlyBillingRunner runner, NightlyBillingRunRepository nightlyRunRepository, BillingRunQueryUseCase queryUseCase, Clock clock) {
        this.runner = runner;
        this.nightlyRunRepository = nightlyRunRepository;
        this.queryUseCase = queryUseCase;
        this.clock = clock;
    }

    @Override
    public NightlyBillingExecutionResponse executeTodaysNightlyBillingRunUnlessItExists() {
        LocalDate today = LocalDate.now(clock);
        Optional<Long> startedRunId = runner.runNightlyBillingSteps(today, NightlyBillingTrigger.MANUAL);
        Long runId = startedRunId.orElseGet(() -> nightlyRunRepository.findByBusinessDate(today).map(NightlyBillingRun::getId).orElseThrow());
        return new NightlyBillingExecutionResponse(startedRunId.isPresent(), queryUseCase.findNightlyBillingRun(runId));
    }
}
