package com.red_escuela.backend.billing.run.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(name = "billing_run")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BillingRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "nightly_billing_run_id", nullable = false, updatable = false)
    private Long nightlyBillingRunId;

    @Enumerated(EnumType.STRING)
    @Column(name = "step", nullable = false, updatable = false)
    private BillingRunStep step;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private BillingRunStatus status;

    @Column(name = "candidates", nullable = false)
    private int candidates;

    @Column(name = "processed", nullable = false)
    private int processed;

    @Column(name = "skipped", nullable = false)
    private int skipped;

    @Column(name = "failed", nullable = false)
    private int failed;

    @Column(name = "not_applicable_code")
    private String notApplicableCode;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

    public static BillingRun startStepRun(Long nightlyBillingRunId, BillingRunStep step, LocalDateTime startedAt) {
        BillingRun run = create(nightlyBillingRunId, step);
        run.status = BillingRunStateMachine.resolveNextStatus(null, BillingRunEvent.STEP_STARTED);
        run.startedAt = Objects.requireNonNull(startedAt, "startedAt");
        return run;
    }

    public static BillingRun declareStepNotApplicable(Long nightlyBillingRunId, BillingRunStep step, String notApplicableCode, LocalDateTime at) {
        BillingRun run = create(nightlyBillingRunId, step);
        run.status = BillingRunStateMachine.resolveNextStatus(null, BillingRunEvent.STEP_DECLARED_NOT_APPLICABLE);
        run.notApplicableCode = Objects.requireNonNull(notApplicableCode, "notApplicableCode");
        run.startedAt = Objects.requireNonNull(at, "at");
        run.finishedAt = at;
        return run;
    }

    public void finishStepRun(BillingRunStepOutcome outcome, LocalDateTime finishedAt) {
        BillingRunEvent event = outcome.hasErrors() ? BillingRunEvent.STEP_FINISHED_WITH_ERRORS : BillingRunEvent.STEP_FINISHED_CLEAN;
        this.status = BillingRunStateMachine.resolveNextStatus(status, event);
        this.candidates = outcome.candidates();
        this.processed = outcome.processed();
        this.skipped = outcome.skipped();
        this.failed = outcome.failed();
        this.finishedAt = Objects.requireNonNull(finishedAt, "finishedAt");
    }

    public void abortStepRun(LocalDateTime finishedAt) {
        this.status = BillingRunStateMachine.resolveNextStatus(status, BillingRunEvent.STEP_ABORTED);
        this.finishedAt = Objects.requireNonNull(finishedAt, "finishedAt");
    }

    private static BillingRun create(Long nightlyBillingRunId, BillingRunStep step) {
        BillingRun run = new BillingRun();
        run.nightlyBillingRunId = Objects.requireNonNull(nightlyBillingRunId, "nightlyBillingRunId");
        run.step = Objects.requireNonNull(step, "step");
        return run;
    }
}
