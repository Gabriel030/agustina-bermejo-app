package com.red_escuela.backend.billing.payment.domain;

import com.red_escuela.backend.billing.account.domain.StudentCurrentAccount;
import com.red_escuela.backend.billing.invoice.exception.StudentCurrentAccountCurrencyMismatchException;
import com.red_escuela.backend.billing.payment.exception.IllegalPaymentStatusTransitionException;
import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.shared.domain.NonNegativeMoney;
import com.red_escuela.backend.shared.domain.statemachine.StatusChangeActor;
import com.red_escuela.backend.shared.time.ApplicationTime;
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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

@Entity
@Table(name = "payment")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Column(name = "student_current_account_id", nullable = false, updatable = false)
    private Long studentCurrentAccountId;

    @Enumerated(EnumType.STRING)
    @Column(name = "currency", nullable = false, updatable = false)
    private Currency currency;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private PaymentStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "gateway", nullable = false, updatable = false)
    private PaymentGatewayCode gateway;

    @Enumerated(EnumType.STRING)
    @Column(name = "method_type", nullable = false, updatable = false)
    private PaymentMethodType methodType;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "value.amount", column = @Column(name = "amount", nullable = false)),
            @AttributeOverride(name = "value.currency", column = @Column(name = "amount_currency", nullable = false))})
    private NonNegativeMoney amount;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "value.amount", column = @Column(name = "approved_amount")),
            @AttributeOverride(name = "value.currency", column = @Column(name = "approved_amount_currency"))})
    private NonNegativeMoney approvedAmount;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "value.amount", column = @Column(name = "refunded_amount", nullable = false)),
            @AttributeOverride(name = "value.currency", column = @Column(name = "refunded_amount_currency", nullable = false))})
    private NonNegativeMoney refundedAmount;

    @Column(name = "gateway_payment_id", length = 120)
    private String gatewayPaymentId;

    @Column(name = "gateway_raw_status", length = 120)
    private String gatewayRawStatus;

    @Column(name = "gateway_raw_status_detail", length = 255)
    private String gatewayRawStatusDetail;

    @Column(name = "paid_on")
    private LocalDate paidOn;

    @Column(name = "registered_at", nullable = false, updatable = false)
    private LocalDateTime registeredAt;

    @Column(name = "started_at", nullable = false, updatable = false)
    private LocalDateTime startedAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "method_details", nullable = false, columnDefinition = "jsonb")
    private PaymentMethodDetails methodDetails;

    @Column(name = "dispute_opened_at")
    private LocalDateTime disputeOpenedAt;

    @Column(name = "dispute_closed_at")
    private LocalDateTime disputeClosedAt;

    @Column(name = "registered_by_person_id", updatable = false)
    private Long registeredByPersonId;

    @Column(name = "registered_by_process", length = 80, updatable = false)
    private String registeredByProcess;

    @Column(name = "reviewed_by_person_id")
    private Long reviewedByPersonId;

    @Column(name = "review_note", length = 500)
    private String reviewNote;

    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;

    @Column(name = "idempotency_key", length = 100, updatable = false)
    private String idempotencyKey;

    @OneToMany(mappedBy = "payment", cascade = CascadeType.ALL, orphanRemoval = false)
    @OrderBy("id ASC")
    @Getter(AccessLevel.NONE)
    private List<PaymentStatusChange> statusChanges = new ArrayList<>();

    private Payment(StudentCurrentAccount account, NonNegativeMoney amount, PaymentGatewayCode gateway, PaymentMethodDetails methodDetails,
                    String idempotencyKey, StatusChangeActor actor) {
        Objects.requireNonNull(account, "account");
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(gateway, "gateway");
        Objects.requireNonNull(methodDetails, "methodDetails");
        Objects.requireNonNull(actor, "actor");
        if (amount.value().currency() != account.getCurrency()) {
            throw new StudentCurrentAccountCurrencyMismatchException();
        }
        if (amount.value().isZero()) {
            throw new IllegalArgumentException("A payment amount must be greater than zero");
        }
        this.studentCurrentAccountId = account.getId();
        this.currency = account.getCurrency();
        this.gateway = gateway;
        this.methodType = methodDetails.methodType();
        this.methodDetails = methodDetails;
        this.amount = amount;
        this.refundedAmount = NonNegativeMoney.zero(account.getCurrency());
        this.idempotencyKey = idempotencyKey;
        this.registeredByPersonId = actor.personId();
        this.registeredByProcess = actor.processName();
        LocalDateTime now = ApplicationTime.now();
        this.registeredAt = now;
        this.startedAt = now;
    }

    public static Payment registerApprovedCashPayment(StudentCurrentAccount account, NonNegativeMoney amount, LocalDate paidOn,
                                                      CashPaymentDetails details, String idempotencyKey, StatusChangeActor actor) {
        Objects.requireNonNull(paidOn, "paidOn");
        Payment payment = new Payment(account, amount, PaymentGatewayCode.MANUAL, details, idempotencyKey, actor);
        payment.approvedAmount = amount;
        payment.paidOn = paidOn;
        payment.changeStatus(PaymentEvent.CASH_PAYMENT_REGISTERED, actor, null, GatewayRawStatus.none());
        return payment;
    }

    public static Payment createPendingBankTransferPayment(StudentCurrentAccount account, NonNegativeMoney declaredAmount,
                                                           BankTransferPaymentDetails details, String idempotencyKey, StatusChangeActor actor) {
        Payment payment = new Payment(account, declaredAmount, PaymentGatewayCode.MANUAL, details, idempotencyKey, actor);
        payment.changeStatus(PaymentEvent.PENDING_BANK_TRANSFER_CREATED, actor, null, GatewayRawStatus.none());
        return payment;
    }

    public static Payment createPendingGatewayPayment(StudentCurrentAccount account, NonNegativeMoney amount, PaymentGatewayCode gateway,
                                                      PaymentMethodDetails details, String gatewayPaymentId, GatewayRawStatus rawStatus,
                                                      String idempotencyKey, StatusChangeActor actor) {
        Payment payment = new Payment(account, amount, gateway, details, idempotencyKey, actor);
        payment.gatewayPaymentId = gatewayPaymentId;
        payment.changeStatus(PaymentEvent.PENDING_ONLINE_PAYMENT_CREATED, actor, null, rawStatus);
        return payment;
    }

    public void approvePayment(NonNegativeMoney verifiedAmount, LocalDate paidOn, StatusChangeActor reviewer, String reviewNote) {
        Objects.requireNonNull(verifiedAmount, "verifiedAmount");
        Objects.requireNonNull(paidOn, "paidOn");
        if (verifiedAmount.value().currency() != currency) {
            throw new StudentCurrentAccountCurrencyMismatchException();
        }
        if (verifiedAmount.value().isZero()) {
            throw new IllegalArgumentException("A verified payment amount must be greater than zero");
        }
        requireManualGateway(PaymentEvent.PAYMENT_APPROVED);
        resolveNextStatusFor(PaymentEvent.PAYMENT_APPROVED);
        this.approvedAmount = verifiedAmount;
        this.paidOn = paidOn;
        this.reviewedByPersonId = reviewer.personId();
        this.reviewNote = reviewNote;
        changeStatus(PaymentEvent.PAYMENT_APPROVED, reviewer, reviewNote, GatewayRawStatus.none());
    }

    public void rejectPayment(String reason, StatusChangeActor reviewer) {
        requireManualGateway(PaymentEvent.PAYMENT_REJECTED);
        resolveNextStatusFor(PaymentEvent.PAYMENT_REJECTED);
        this.rejectionReason = reason;
        this.reviewedByPersonId = reviewer.personId();
        changeStatus(PaymentEvent.PAYMENT_REJECTED, reviewer, reason, GatewayRawStatus.none());
    }

    public void cancelPendingPayment(String reason, StatusChangeActor actor) {
        changeStatus(PaymentEvent.PENDING_PAYMENT_CANCELLED, actor, reason, GatewayRawStatus.none());
    }

    public void revokeBankTransferApproval(String reason, StatusChangeActor reviewer) {
        resolveNextStatusFor(PaymentEvent.BANK_TRANSFER_APPROVAL_REVOKED);
        this.rejectionReason = reason;
        this.reviewedByPersonId = reviewer.personId();
        changeStatus(PaymentEvent.BANK_TRANSFER_APPROVAL_REVOKED, reviewer, reason, GatewayRawStatus.none());
    }

    public List<PaymentStatusChange> statusChanges() {
        return Collections.unmodifiableList(statusChanges);
    }

    void changeStatus(PaymentEvent event, StatusChangeActor actor, String reason, GatewayRawStatus rawStatus) {
        PaymentStatus nextStatus = PaymentStatusStateMachine.resolveNextStatus(status, event);
        requireRevocationOnlyOfManualBankTransfer(event);
        PaymentStatus previousStatus = status;
        this.status = nextStatus;
        if (!rawStatus.equals(GatewayRawStatus.none())) {
            this.gatewayRawStatus = rawStatus.status();
            this.gatewayRawStatusDetail = rawStatus.detail();
        }
        statusChanges.add(new PaymentStatusChange(this, previousStatus, nextStatus, event, reason, rawStatus, actor));
    }

    private void resolveNextStatusFor(PaymentEvent event) {
        PaymentStatusStateMachine.resolveNextStatus(status, event);
        requireRevocationOnlyOfManualBankTransfer(event);
    }

    private void requireManualGateway(PaymentEvent event) {
        if (gateway != PaymentGatewayCode.MANUAL) {
            throw new IllegalPaymentStatusTransitionException(status, event);
        }
    }

    private void requireRevocationOnlyOfManualBankTransfer(PaymentEvent event) {
        boolean manualBankTransfer = gateway == PaymentGatewayCode.MANUAL && methodType == PaymentMethodType.BANK_TRANSFER;
        if (event == PaymentEvent.BANK_TRANSFER_APPROVAL_REVOKED && !manualBankTransfer) {
            throw new IllegalPaymentStatusTransitionException(status, event);
        }
    }
}
