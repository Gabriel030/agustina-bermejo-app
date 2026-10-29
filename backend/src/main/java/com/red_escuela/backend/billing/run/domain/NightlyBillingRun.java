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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(name = "nightly_billing_run")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NightlyBillingRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "business_date", nullable = false, updatable = false)
    private LocalDate businessDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private NightlyBillingRunStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "trigger", nullable = false, updatable = false)
    private NightlyBillingTrigger trigger;

    @Column(name = "started_at", nullable = false, updatable = false)
    private LocalDateTime startedAt;

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

    public static NightlyBillingRun startNightlyRun(LocalDate businessDate, NightlyBillingTrigger trigger, LocalDateTime startedAt) {
        NightlyBillingRun run = new NightlyBillingRun();
        run.businessDate = Objects.requireNonNull(businessDate, "businessDate");
        run.trigger = Objects.requireNonNull(trigger, "trigger");
        run.startedAt = Objects.requireNonNull(startedAt, "startedAt");
        run.status = NightlyBillingRunStateMachine.resolveNextStatus(null, NightlyBillingRunEvent.NIGHT_STARTED);
        return run;
    }

    public void finishNightlyRun(NightlyBillingRunEvent event, LocalDateTime finishedAt) {
        this.status = NightlyBillingRunStateMachine.resolveNextStatus(status, event);
        this.finishedAt = Objects.requireNonNull(finishedAt, "finishedAt");
    }
}
