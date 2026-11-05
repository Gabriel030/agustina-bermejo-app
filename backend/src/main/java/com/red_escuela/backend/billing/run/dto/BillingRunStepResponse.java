package com.red_escuela.backend.billing.run.dto;

import com.red_escuela.backend.billing.run.domain.BillingRun;
import com.red_escuela.backend.billing.run.domain.BillingRunStatus;
import com.red_escuela.backend.billing.run.domain.BillingRunStep;

import java.time.LocalDateTime;

public record BillingRunStepResponse(Long id, BillingRunStep step, BillingRunStatus status, int candidates, int processed, int skipped, int failed, String notApplicableCode,
                                     LocalDateTime startedAt, LocalDateTime finishedAt) {

    public static BillingRunStepResponse of(BillingRun run) {
        return new BillingRunStepResponse(run.getId(), run.getStep(), run.getStatus(), run.getCandidates(), run.getProcessed(), run.getSkipped(), run.getFailed(),
                run.getNotApplicableCode(), run.getStartedAt(), run.getFinishedAt());
    }
}
