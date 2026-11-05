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
@Table(name = "monthly_invoice_run_item_attempt")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MonthlyInvoiceRunItemAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "monthly_invoice_run_item_id", nullable = false, updatable = false)
    private Long monthlyInvoiceRunItemId;

    @Column(name = "attempt_number", nullable = false, updatable = false)
    private int attemptNumber;

    @Column(name = "attempted_at", nullable = false, updatable = false)
    private LocalDateTime attemptedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "result", nullable = false, updatable = false)
    private MonthlyInvoiceRunItemResult result;

    @Column(name = "error_code", updatable = false)
    private String errorCode;

    @Column(name = "message", updatable = false)
    private String message;

    public static MonthlyInvoiceRunItemAttempt recordAttempt(Long itemId, int attemptNumber, LocalDateTime attemptedAt, MonthlyInvoiceItemOutcome outcome) {
        MonthlyInvoiceRunItemAttempt attempt = new MonthlyInvoiceRunItemAttempt();
        attempt.monthlyInvoiceRunItemId = Objects.requireNonNull(itemId, "itemId");
        attempt.attemptNumber = attemptNumber;
        attempt.attemptedAt = Objects.requireNonNull(attemptedAt, "attemptedAt");
        attempt.result = outcome.result();
        attempt.errorCode = outcome.errorCode();
        attempt.message = outcome.message();
        return attempt;
    }
}
