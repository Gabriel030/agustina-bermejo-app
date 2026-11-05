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

import java.util.Objects;

@Entity
@Table(name = "monthly_invoice_simulation_alert")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MonthlyInvoiceSimulationAlert {
    private static final int MESSAGE_LIMIT = 500;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "monthly_invoice_run_id", nullable = false, updatable = false)
    private Long monthlyInvoiceRunId;

    @Enumerated(EnumType.STRING)
    @Column(name = "alert_type", nullable = false, updatable = false)
    private MonthlyInvoiceSimulationAlertType alertType;

    @Enumerated(EnumType.STRING)
    @Column(name = "enrollment_type", nullable = false, updatable = false)
    private MonthlyInvoiceEnrollmentType enrollmentType;

    @Column(name = "offering_id", nullable = false, updatable = false)
    private Long offeringId;

    @Column(name = "affected_enrollments", nullable = false, updatable = false)
    private int affectedEnrollments;

    @Column(name = "message", nullable = false, updatable = false)
    private String message;

    public static MonthlyInvoiceSimulationAlert raiseAlert(Long runId, MonthlyInvoiceSimulationAlertType type, MonthlyInvoiceEnrollmentType enrollmentType, Long offeringId,
                                                           int affectedEnrollments, String message) {
        MonthlyInvoiceSimulationAlert alert = new MonthlyInvoiceSimulationAlert();
        alert.monthlyInvoiceRunId = Objects.requireNonNull(runId, "runId");
        alert.alertType = Objects.requireNonNull(type, "type");
        alert.enrollmentType = Objects.requireNonNull(enrollmentType, "enrollmentType");
        alert.offeringId = Objects.requireNonNull(offeringId, "offeringId");
        alert.affectedEnrollments = affectedEnrollments;
        alert.message = message.substring(0, Math.min(message.length(), MESSAGE_LIMIT));
        return alert;
    }
}
