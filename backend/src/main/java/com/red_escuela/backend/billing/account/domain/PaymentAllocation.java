package com.red_escuela.backend.billing.account.domain;

import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.shared.domain.NonNegativeMoney;
import com.red_escuela.backend.shared.domain.statemachine.StatusChangeActor;
import com.red_escuela.backend.shared.time.ApplicationTime;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
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
import java.util.Objects;

@Entity
@Immutable
@Table(name = "payment_allocation")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PaymentAllocation {

    private static final int MAX_REASON_LENGTH = 500;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "student_current_account_id", nullable = false, updatable = false)
    private Long studentCurrentAccountId;

    @Column(name = "invoice_id", nullable = false, updatable = false)
    private Long invoiceId;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "value.amount", column = @Column(name = "amount", nullable = false, updatable = false)),
            @AttributeOverride(name = "value.currency", column = @Column(name = "amount_currency", nullable = false, updatable = false))})
    private NonNegativeMoney amount;

    @Column(name = "effective_date", nullable = false, updatable = false)
    private LocalDate effectiveDate;

    @Column(name = "allocated_on", nullable = false, updatable = false)
    private LocalDate allocatedOn;

    @Column(name = "reverses_allocation_id", updatable = false)
    private Long reversesAllocationId;

    @Column(name = "reason", updatable = false, length = MAX_REASON_LENGTH)
    private String reason;

    @Column(name = "actor_person_id", updatable = false)
    private Long actorPersonId;

    @Column(name = "actor_process", updatable = false, length = 80)
    private String actorProcess;

    @Column(name = "recorded_at", nullable = false, updatable = false)
    private LocalDateTime recordedAt;

    private PaymentAllocation(Long studentCurrentAccountId, Long invoiceId, NonNegativeMoney amount, LocalDate effectiveDate, LocalDate allocatedOn,
                              Long reversesAllocationId, String reason, StatusChangeActor actor) {
        this.studentCurrentAccountId = studentCurrentAccountId;
        this.invoiceId = invoiceId;
        this.amount = amount;
        this.effectiveDate = effectiveDate;
        this.allocatedOn = allocatedOn;
        this.reversesAllocationId = reversesAllocationId;
        this.reason = reason;
        this.actorPersonId = actor.personId();
        this.actorProcess = actor.processName();
        this.recordedAt = ApplicationTime.now();
    }

    public static PaymentAllocation recordFullInvoiceAllocation(Invoice invoice, NonNegativeMoney amount, LocalDate effectiveDate, LocalDate allocatedOn,
                                                                StatusChangeActor actor) {
        Objects.requireNonNull(invoice, "invoice");
        Objects.requireNonNull(invoice.getId(), "invoice.id");
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(effectiveDate, "effectiveDate");
        Objects.requireNonNull(allocatedOn, "allocatedOn");
        Objects.requireNonNull(actor, "actor");
        if (amount.value().isZero()) {
            throw new IllegalArgumentException("An allocation amount must be greater than zero");
        }
        return new PaymentAllocation(invoice.getStudentCurrentAccountId(), invoice.getId(), amount, effectiveDate, allocatedOn, null, null, actor);
    }

    public PaymentAllocation recordAllocationReversal(LocalDate reversedOn, String reason, StatusChangeActor actor) {
        Objects.requireNonNull(reversedOn, "reversedOn");
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(id, "id");
        if (isReversal()) {
            throw new IllegalStateException("A reversal cannot be reversed");
        }
        if (reason == null || reason.isBlank() || reason.length() > MAX_REASON_LENGTH) {
            throw new IllegalArgumentException("The reversal reason must have between 1 and " + MAX_REASON_LENGTH + " characters");
        }
        return new PaymentAllocation(studentCurrentAccountId, invoiceId, amount, effectiveDate, reversedOn, id, reason, actor);
    }

    public boolean isReversal() {
        return reversesAllocationId != null;
    }
}
