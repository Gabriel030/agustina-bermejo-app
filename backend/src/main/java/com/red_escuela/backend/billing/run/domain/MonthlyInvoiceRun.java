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
@Table(name = "monthly_invoice_run")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MonthlyInvoiceRun {
    public static final String MAX_RUNNING_TIME_ISO = "PT2H";
    public static final String INTERRUPTED_FAILURE_CODE = "INTERRUPTED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "period", nullable = false, updatable = false)
    private LocalDate period;

    @Enumerated(EnumType.STRING)
    @Column(name = "mode", nullable = false, updatable = false)
    private MonthlyInvoiceRunMode mode;

    @Enumerated(EnumType.STRING)
    @Column(name = "issuance_mode", nullable = false, updatable = false)
    private MonthlyInvoiceIssuanceMode issuanceMode;

    @Column(name = "business_date", nullable = false, updatable = false)
    private LocalDate businessDate;

    @Column(name = "pricing_date", nullable = false, updatable = false)
    private LocalDate pricingDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private MonthlyInvoiceRunStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "trigger", nullable = false, updatable = false)
    private MonthlyInvoiceRunTrigger trigger;

    @Column(name = "failure_code")
    private String failureCode;

    @Column(name = "started_at", nullable = false, updatable = false)
    private LocalDateTime startedAt;

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

    public static MonthlyInvoiceRun startRealDirectRun(LocalDate period, LocalDate businessDate, MonthlyInvoiceRunTrigger trigger, LocalDateTime startedAt) {
        MonthlyInvoiceRun run = base(period, businessDate, trigger, startedAt);
        run.status = MonthlyInvoiceRunStateMachine.resolveNextStatus(null, MonthlyInvoiceRunEvent.RUN_STARTED);
        return run;
    }

    public static MonthlyInvoiceRun startSimulationRun(LocalDate period, LocalDate businessDate, LocalDate pricingDate, MonthlyInvoiceRunTrigger trigger, LocalDateTime startedAt) {
        MonthlyInvoiceRun run = base(period, businessDate, trigger, startedAt);
        run.mode = MonthlyInvoiceRunMode.SIMULATION;
        run.pricingDate = Objects.requireNonNull(pricingDate, "pricingDate");
        run.status = MonthlyInvoiceRunStateMachine.resolveNextStatus(null, MonthlyInvoiceRunEvent.RUN_STARTED);
        return run;
    }

    public static MonthlyInvoiceRun recordRunBlockedByNight(LocalDate period, LocalDate businessDate, MonthlyInvoiceRunTrigger trigger, String failureCode,
                                                            LocalDateTime at) {
        MonthlyInvoiceRun run = base(period, businessDate, trigger, at);
        run.status = MonthlyInvoiceRunStateMachine.resolveNextStatus(null, MonthlyInvoiceRunEvent.RUN_BLOCKED_BY_NIGHT);
        run.failureCode = Objects.requireNonNull(failureCode, "failureCode");
        run.finishedAt = at;
        return run;
    }

    public void finishMonthlyRun(MonthlyInvoiceRunEvent event, LocalDateTime finishedAt) {
        this.status = MonthlyInvoiceRunStateMachine.resolveNextStatus(status, event);
        this.finishedAt = Objects.requireNonNull(finishedAt, "finishedAt");
    }

    public void abortMonthlyRun(String failureCode, LocalDateTime finishedAt) {
        finishMonthlyRun(MonthlyInvoiceRunEvent.RUN_ABORTED, finishedAt);
        this.failureCode = Objects.requireNonNull(failureCode, "failureCode");
    }

    public void abortInterruptedRun(LocalDateTime finishedAt) {
        abortMonthlyRun(INTERRUPTED_FAILURE_CODE, finishedAt);
    }

    private static MonthlyInvoiceRun base(LocalDate period, LocalDate businessDate, MonthlyInvoiceRunTrigger trigger, LocalDateTime startedAt) {
        MonthlyInvoiceRun run = new MonthlyInvoiceRun();
        run.period = Objects.requireNonNull(period, "period");
        run.businessDate = Objects.requireNonNull(businessDate, "businessDate");
        run.pricingDate = businessDate;
        run.mode = MonthlyInvoiceRunMode.REAL;
        run.issuanceMode = MonthlyInvoiceIssuanceMode.DIRECT;
        run.trigger = Objects.requireNonNull(trigger, "trigger");
        run.startedAt = Objects.requireNonNull(startedAt, "startedAt");
        return run;
    }
}
