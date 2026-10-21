package com.red_escuela.backend.billing.payment.domain;

import com.red_escuela.backend.shared.domain.NonNegativeMoney;
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

import java.util.Objects;

@Entity
@Table(name = "payment_checkout_item")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PaymentCheckoutItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "payment_id", nullable = false, updatable = false)
    private Long paymentId;

    @Column(name = "student_current_account_id", nullable = false, updatable = false)
    private Long studentCurrentAccountId;

    @Column(name = "invoice_id", nullable = false, updatable = false)
    private Long invoiceId;

    @Column(name = "position", nullable = false, updatable = false)
    private int position;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "value.amount", column = @Column(name = "outstanding_at_selection", nullable = false, updatable = false)),
            @AttributeOverride(name = "value.currency", column = @Column(name = "outstanding_at_selection_currency", nullable = false, updatable = false))})
    private NonNegativeMoney outstandingAtSelection;

    @Column(name = "marked_under_review", nullable = false)
    private boolean markedUnderReview;

    private PaymentCheckoutItem(Long paymentId, Long studentCurrentAccountId, PlannedCheckoutItem planned) {
        this.paymentId = Objects.requireNonNull(paymentId, "paymentId");
        this.studentCurrentAccountId = Objects.requireNonNull(studentCurrentAccountId, "studentCurrentAccountId");
        this.invoiceId = Objects.requireNonNull(planned.invoiceId(), "invoiceId");
        if (planned.position() < 1) {
            throw new IllegalArgumentException("A checkout item position starts at 1");
        }
        if (planned.outstandingAtSelection().amount().signum() <= 0) {
            throw new IllegalArgumentException("The outstanding amount at selection must be greater than zero");
        }
        this.position = planned.position();
        this.outstandingAtSelection = NonNegativeMoney.of(planned.outstandingAtSelection());
        this.markedUnderReview = planned.protectedFromOverdue();
    }

    public static PaymentCheckoutItem of(Long paymentId, Long studentCurrentAccountId, PlannedCheckoutItem planned) {
        return new PaymentCheckoutItem(paymentId, studentCurrentAccountId, planned);
    }
}
