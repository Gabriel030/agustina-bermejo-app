package com.red_escuela.backend.billing.run.dto;

import com.red_escuela.backend.billing.run.domain.BillingRun;
import com.red_escuela.backend.billing.run.domain.BillingRunStatus;
import com.red_escuela.backend.billing.run.domain.NightlyBillingRun;
import com.red_escuela.backend.billing.run.domain.NightlyBillingRunStatus;
import com.red_escuela.backend.billing.run.domain.NightlyBillingTrigger;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record NightlyBillingRunResponse(Long id, LocalDate businessDate, NightlyBillingRunStatus status, NightlyBillingTrigger trigger, LocalDateTime startedAt,
                                        LocalDateTime finishedAt, Long durationSeconds, int stepsWithErrors, int failedSteps, List<BillingRunStepResponse> steps) {

    public static NightlyBillingRunResponse of(NightlyBillingRun run, List<BillingRun> stepRuns) {
        int withErrors = (int) stepRuns.stream().filter(step -> step.getStatus() == BillingRunStatus.COMPLETED_WITH_ERRORS).count();
        int failed = (int) stepRuns.stream().filter(step -> step.getStatus() == BillingRunStatus.FAILED).count();
        Long duration = run.getFinishedAt() == null ? null : Duration.between(run.getStartedAt(), run.getFinishedAt()).toSeconds();
        return new NightlyBillingRunResponse(run.getId(), run.getBusinessDate(), run.getStatus(), run.getTrigger(), run.getStartedAt(), run.getFinishedAt(), duration,
                withErrors, failed, stepRuns.stream().map(BillingRunStepResponse::of).toList());
    }
}
