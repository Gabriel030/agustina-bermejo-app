package com.red_escuela.backend.billing.payment.domain;

import com.red_escuela.backend.billing.payment.exception.RefundRequestNotPendingException;
import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.shared.domain.NonNegativeMoney;
import com.red_escuela.backend.shared.domain.statemachine.StatusChangeActor;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

@Entity
@Table(name = "refund")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Refund {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Column(name = "refund_request_id", nullable = false, updatable = false)
    private Long refundRequestId;

    @Column(name = "payment_id", nullable = false, updatable = false)
    private Long paymentId;

    @Column(name = "student_current_account_id", nullable = false, updatable = false)
    private Long studentCurrentAccountId;

    @Enumerated(EnumType.STRING)
    @Column(name = "currency", nullable = false, updatable = false)
    private Currency currency;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private RefundStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "method_type", nullable = false, updatable = false)
    private PaymentMethodType methodType;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "value.amount", column = @Column(name = "amount", nullable = false, updatable = false)),
            @AttributeOverride(name = "value.currency", column = @Column(name = "amount_currency", nullable = false, updatable = false))})
    private NonNegativeMoney amount;

    @Column(name = "destination_account", nullable = false, length = 50, updatable = false)
    private String destinationAccount;

    @Column(name = "destination_holder", nullable = false, length = 120, updatable = false)
    private String destinationHolder;

    @Column(name = "requested_by_person_id", nullable = false, updatable = false)
    private Long requestedByPersonId;

    @Column(name = "authorization_request_id", updatable = false)
    private Long authorizationRequestId;

    @Column(name = "requested_on", nullable = false, updatable = false)
    private LocalDate requestedOn;

    @Column(name = "approved_on")
    private LocalDate approvedOn;

    @Column(name = "completed_on")
    private LocalDate completedOn;

    @Column(name = "completion_reference", length = 120)
    private String completionReference;

    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;

    @OneToMany(mappedBy = "refund", cascade = CascadeType.ALL, orphanRemoval = false)
    @OrderBy("id ASC")
    @Getter(AccessLevel.NONE)
    private List<RefundStatusChange> statusChanges = new ArrayList<>();

    private Refund(Long refundRequestId, Long studentCurrentAccountId, Currency currency, RefundPart part, Long requestedByPersonId,
                   Long authorizationRequestId, LocalDate requestedOn) {
        this.refundRequestId = Objects.requireNonNull(refundRequestId, "refundRequestId");
        this.studentCurrentAccountId = Objects.requireNonNull(studentCurrentAccountId, "studentCurrentAccountId");
        this.currency = Objects.requireNonNull(currency, "currency");
        this.paymentId = part.paymentId();
        this.methodType = part.methodType();
        this.amount = part.amount();
        this.destinationAccount = part.destination().account();
        this.destinationHolder = part.destination().holder();
        this.requestedByPersonId = Objects.requireNonNull(requestedByPersonId, "requestedByPersonId");
        this.authorizationRequestId = authorizationRequestId;
        this.requestedOn = Objects.requireNonNull(requestedOn, "requestedOn");
    }

    public static Refund requestPartOf(Long refundRequestId, Long studentCurrentAccountId, RefundPart part, Long requestedByPersonId,
                                       Long authorizationRequestId, LocalDate requestedOn) {
        Objects.requireNonNull(part, "part");
        if (part.amount().value().isZero()) {
            throw new IllegalArgumentException("A refund amount must be greater than zero");
        }
        if (!part.destination().isComplete()) {
            throw new IllegalArgumentException("A refund needs a destination account and holder");
        }
        Refund refund = new Refund(refundRequestId, studentCurrentAccountId, part.amount().value().currency(), part, requestedByPersonId, authorizationRequestId,
                requestedOn);
        refund.changeStatus(RefundEvent.REFUND_REQUESTED, StatusChangeActor.ofPerson(requestedByPersonId), null);
        return refund;
    }

    public static void requireAnyRequestedPart(List<Refund> requestedParts) {
        if (requestedParts.isEmpty()) {
            throw new RefundRequestNotPendingException();
        }
    }

    public void approve(LocalDate approvedOn, StatusChangeActor actor) {
        Objects.requireNonNull(approvedOn, "approvedOn");
        requireTransitionBy(RefundEvent.REFUND_APPROVED);
        this.approvedOn = approvedOn;
        changeStatus(RefundEvent.REFUND_APPROVED, actor, null);
    }

    public void reject(String reason, StatusChangeActor actor) {
        Objects.requireNonNull(reason, "reason");
        requireTransitionBy(RefundEvent.REFUND_REJECTED);
        this.rejectionReason = reason;
        changeStatus(RefundEvent.REFUND_REJECTED, actor, reason);
    }

    public void complete(LocalDate completedOn, String reference, StatusChangeActor actor) {
        Objects.requireNonNull(completedOn, "completedOn");
        Objects.requireNonNull(reference, "reference");
        requireTransitionBy(RefundEvent.REFUND_COMPLETED);
        this.completedOn = completedOn;
        this.completionReference = reference;
        changeStatus(RefundEvent.REFUND_COMPLETED, actor, reference);
    }

    public void fail(String reason, StatusChangeActor actor) {
        Objects.requireNonNull(reason, "reason");
        requireTransitionBy(RefundEvent.REFUND_FAILED);
        this.failureReason = reason;
        changeStatus(RefundEvent.REFUND_FAILED, actor, reason);
    }

    public void requireTransitionBy(RefundEvent event) {
        RefundStatusStateMachine.resolveNextStatus(status, event);
    }

    public boolean isPending() {
        return status == RefundStatus.REQUESTED || status == RefundStatus.APPROVED;
    }

    public List<RefundStatusChange> statusChanges() {
        return Collections.unmodifiableList(statusChanges);
    }

    private void changeStatus(RefundEvent event, StatusChangeActor actor, String reason) {
        Objects.requireNonNull(actor, "actor");
        RefundStatus nextStatus = RefundStatusStateMachine.resolveNextStatus(status, event);
        RefundStatus previousStatus = status;
        this.status = nextStatus;
        statusChanges.add(new RefundStatusChange(this, previousStatus, nextStatus, event, reason, actor));
    }
}
