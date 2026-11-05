package com.red_escuela.backend.billing.account.domain;

import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.shared.domain.NonNegativeMoney;
import com.red_escuela.backend.shared.time.ApplicationTime;
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
import org.hibernate.annotations.Immutable;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Immutable
@Table(name = "current_account_entry")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CurrentAccountEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "student_current_account_id", nullable = false, updatable = false)
    private Long studentCurrentAccountId;

    @Enumerated(EnumType.STRING)
    @Column(name = "currency", nullable = false, updatable = false)
    private Currency currency;

    @Column(name = "sequence_number", nullable = false, updatable = false)
    private long sequenceNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "direction", nullable = false, updatable = false)
    private CurrentAccountEntryDirection direction;

    @Enumerated(EnumType.STRING)
    @Column(name = "entry_type", nullable = false, updatable = false)
    private CurrentAccountEntryType entryType;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "value.amount", column = @Column(name = "amount", nullable = false, updatable = false)),
            @AttributeOverride(name = "value.currency", column = @Column(name = "amount_currency", nullable = false, updatable = false))})
    private NonNegativeMoney amount;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "amount.value.amount", column = @Column(name = "running_balance_amount", nullable = false, updatable = false)),
            @AttributeOverride(name = "amount.value.currency", column = @Column(name = "running_balance_amount_currency", nullable = false, updatable = false)),
            @AttributeOverride(name = "side", column = @Column(name = "running_balance_side", nullable = false, updatable = false))})
    private CurrentAccountBalance runningBalance;

    @Column(name = "effective_date", nullable = false, updatable = false)
    private LocalDate effectiveDate;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "value.amount", column = @Column(name = "allocatable_amount", updatable = false)),
            @AttributeOverride(name = "value.currency", column = @Column(name = "allocatable_amount_currency", updatable = false))})
    private NonNegativeMoney allocatableAmount;

    @Column(name = "recorded_at", nullable = false, updatable = false)
    private LocalDateTime recordedAt;

    @Column(name = "concept", nullable = false, updatable = false, length = 200)
    private String concept;

    @Column(name = "invoice_id", updatable = false)
    private Long invoiceId;

    @Column(name = "payment_id", updatable = false)
    private Long paymentId;

    @Column(name = "credit_note_id", updatable = false)
    private Long creditNoteId;

    @Column(name = "refund_id", updatable = false)
    private Long refundId;

    @Column(name = "compensates_entry_id", updatable = false)
    private Long compensatesEntryId;

    CurrentAccountEntry(Long studentCurrentAccountId, Currency currency, long sequenceNumber, CurrentAccountEntryType entryType,
                        NonNegativeMoney amount, CurrentAccountBalance runningBalance, LocalDate effectiveDate, String concept,
                        CurrentAccountEntrySource source, NonNegativeMoney allocatableAmount) {
        this.studentCurrentAccountId = studentCurrentAccountId;
        this.currency = currency;
        this.sequenceNumber = sequenceNumber;
        this.direction = entryType.direction();
        this.entryType = entryType;
        this.amount = amount;
        this.allocatableAmount = allocatableAmount;
        this.runningBalance = runningBalance;
        this.effectiveDate = effectiveDate;
        this.recordedAt = ApplicationTime.now();
        this.concept = concept;
        this.invoiceId = source.invoiceId();
        this.paymentId = source.paymentId();
        this.creditNoteId = source.creditNoteId();
        this.refundId = source.refundId();
        this.compensatesEntryId = source.compensatesEntryId();
    }
}
