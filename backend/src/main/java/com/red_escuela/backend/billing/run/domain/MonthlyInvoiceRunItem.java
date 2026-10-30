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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@Entity
@Table(name = "monthly_invoice_run_item")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MonthlyInvoiceRunItem {
    public static final Duration FIRST_RETRY_WAIT = Duration.ofHours(1);

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

    @Enumerated(EnumType.STRING)
    @Column(name = "result", nullable = false)
    private MonthlyInvoiceRunItemResult result;

    @Column(name = "decision_code")
    private String decisionCode;

    @Column(name = "error_code")
    private String errorCode;

    @Column(name = "message")
    private String message;

    @Column(name = "attempts", nullable = false)
    private int attempts;

    @Column(name = "next_retry_at")
    private LocalDateTime nextRetryAt;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "invoice_ids", columnDefinition = "bigint[]")
    private List<Long> invoiceIds;

    public static MonthlyInvoiceRunItem recordItemOutcome(Long runId, MonthlyInvoiceEnrollmentType type, Long enrollmentId, MonthlyInvoiceItemOutcome outcome,
                                                          LocalDateTime recordedAt) {
        boolean failed = outcome.result() == MonthlyInvoiceRunItemResult.FAILED;
        MonthlyInvoiceRunItem item = new MonthlyInvoiceRunItem();
        item.monthlyInvoiceRunId = Objects.requireNonNull(runId, "runId");
        item.enrollmentType = Objects.requireNonNull(type, "type");
        item.enrollmentId = Objects.requireNonNull(enrollmentId, "enrollmentId");
        item.result = outcome.result();
        item.decisionCode = outcome.decisionCode();
        item.errorCode = outcome.errorCode();
        item.message = outcome.message();
        item.invoiceIds = outcome.invoiceIds();
        item.attempts = failed ? 1 : 0;
        item.nextRetryAt = failed ? recordedAt.plus(FIRST_RETRY_WAIT) : null;
        return item;
    }
}
