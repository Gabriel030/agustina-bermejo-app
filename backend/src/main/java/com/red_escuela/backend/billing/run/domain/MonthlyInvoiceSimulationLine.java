package com.red_escuela.backend.billing.run.domain;

import com.red_escuela.backend.shared.domain.NonNegativeMoney;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
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
import java.util.Objects;

@Entity
@Table(name = "monthly_invoice_simulation_line")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MonthlyInvoiceSimulationLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "monthly_invoice_run_id", nullable = false, updatable = false)
    private Long monthlyInvoiceRunId;

    @Enumerated(EnumType.STRING)
    @Column(name = "enrollment_type", nullable = false, updatable = false)
    private MonthlyInvoiceEnrollmentType enrollmentType;

    @Column(name = "enrollment_id", nullable = false, updatable = false)
    private Long enrollmentId;

    @Column(name = "offering_id", updatable = false)
    private Long offeringId;

    @Enumerated(EnumType.STRING)
    @Column(name = "result", nullable = false, updatable = false)
    private MonthlyInvoiceRunItemResult result;

    @Column(name = "decision_code", updatable = false)
    private String decisionCode;

    @Column(name = "error_code", updatable = false)
    private String errorCode;

    @Column(name = "message", updatable = false)
    private String message;

    @Column(name = "charge_count", nullable = false, updatable = false)
    private int chargeCount;

    @Column(name = "due_date", updatable = false)
    private LocalDate dueDate;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "value.amount", column = @Column(name = "total_amount", updatable = false)),
            @AttributeOverride(name = "value.currency", column = @Column(name = "total_currency", updatable = false))})
    private NonNegativeMoney total;

    public static MonthlyInvoiceSimulationLine recordSimulatedOutcome(Long runId, MonthlyInvoiceEnrollmentType type, Long enrollmentId, MonthlyInvoiceSimulationOutcome outcome) {
        MonthlyInvoiceSimulationLine line = new MonthlyInvoiceSimulationLine();
        line.monthlyInvoiceRunId = Objects.requireNonNull(runId, "runId");
        line.enrollmentType = Objects.requireNonNull(type, "type");
        line.enrollmentId = Objects.requireNonNull(enrollmentId, "enrollmentId");
        line.offeringId = outcome.offeringId();
        line.result = outcome.result();
        line.decisionCode = outcome.decisionCode();
        line.errorCode = outcome.errorCode();
        line.message = outcome.message();
        line.chargeCount = outcome.chargeCount();
        line.total = outcome.total();
        line.dueDate = outcome.dueDate();
        return line;
    }
}
