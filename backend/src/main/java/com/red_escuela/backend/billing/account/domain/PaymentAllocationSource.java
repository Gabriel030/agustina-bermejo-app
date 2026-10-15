package com.red_escuela.backend.billing.account.domain;

import com.red_escuela.backend.shared.domain.NonNegativeMoney;
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

import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Immutable
@Table(name = "payment_allocation_source")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PaymentAllocationSource {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "payment_allocation_id", nullable = false, updatable = false)
    private Long paymentAllocationId;

    @Column(name = "student_current_account_id", nullable = false, updatable = false)
    private Long studentCurrentAccountId;

    @Column(name = "credit_entry_id", nullable = false, updatable = false)
    private Long creditEntryId;

    @Column(name = "payment_id", updatable = false)
    private Long paymentId;

    @Column(name = "credit_note_id", updatable = false)
    private Long creditNoteId;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "value.amount", column = @Column(name = "amount", nullable = false, updatable = false)),
            @AttributeOverride(name = "value.currency", column = @Column(name = "amount_currency", nullable = false, updatable = false))})
    private NonNegativeMoney amount;

    @Column(name = "reverses_source_id", updatable = false)
    private Long reversesSourceId;

    @Column(name = "recorded_at", nullable = false, updatable = false)
    private LocalDateTime recordedAt;

    private PaymentAllocationSource(PaymentAllocation allocation, Long creditEntryId, Long paymentId, Long creditNoteId, NonNegativeMoney amount,
                                    Long reversesSourceId) {
        this.paymentAllocationId = allocation.getId();
        this.studentCurrentAccountId = allocation.getStudentCurrentAccountId();
        this.creditEntryId = creditEntryId;
        this.paymentId = paymentId;
        this.creditNoteId = creditNoteId;
        this.amount = amount;
        this.reversesSourceId = reversesSourceId;
        this.recordedAt = ApplicationTime.now();
    }

    public static PaymentAllocationSource recordSourceOf(PaymentAllocation allocation, AllocationSourceShare share) {
        Objects.requireNonNull(allocation, "allocation");
        Objects.requireNonNull(allocation.getId(), "allocation.id");
        Objects.requireNonNull(share, "share");
        if (allocation.isReversal()) {
            throw new IllegalArgumentException("The sources of a reversal are recorded with recordReversalOf");
        }
        return new PaymentAllocationSource(allocation, share.creditEntryId(), share.paymentId(), share.creditNoteId(), share.amount(), null);
    }

    public PaymentAllocationSource recordReversalOf(PaymentAllocation reversal) {
        Objects.requireNonNull(reversal, "reversal");
        Objects.requireNonNull(reversal.getId(), "reversal.id");
        Objects.requireNonNull(id, "id");
        if (isReversal()) {
            throw new IllegalStateException("A reversal source cannot be reversed");
        }
        if (!reversal.isReversal() || !reversal.getReversesAllocationId().equals(paymentAllocationId)) {
            throw new IllegalArgumentException("The reversal must undo the allocation of this source");
        }
        return new PaymentAllocationSource(reversal, creditEntryId, paymentId, creditNoteId, amount, id);
    }

    public boolean isReversal() {
        return reversesSourceId != null;
    }
}
