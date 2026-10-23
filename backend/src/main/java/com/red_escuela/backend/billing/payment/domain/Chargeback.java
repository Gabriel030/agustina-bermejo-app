package com.red_escuela.backend.billing.payment.domain;

import com.red_escuela.backend.billing.payment.exception.ChargebackAlreadyReversedException;
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
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(name = "chargeback")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Chargeback {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Column(name = "payment_id", nullable = false, updatable = false)
    private Long paymentId;

    @Column(name = "student_current_account_id", nullable = false, updatable = false)
    private Long studentCurrentAccountId;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "value.amount", column = @Column(name = "amount", nullable = false, updatable = false)),
            @AttributeOverride(name = "value.currency", column = @Column(name = "amount_currency", nullable = false, updatable = false))})
    private NonNegativeMoney amount;

    @Column(name = "charged_back_on", nullable = false, updatable = false)
    private LocalDate chargedBackOn;

    @Column(name = "gateway_reference", length = 120, updatable = false)
    private String gatewayReference;

    @Column(name = "reversed_on")
    private LocalDate reversedOn;

    @Column(name = "registered_at", nullable = false, updatable = false)
    private LocalDateTime registeredAt;

    @Column(name = "registered_by_person_id", updatable = false)
    private Long registeredByPersonId;

    @Column(name = "registered_by_process", length = 80, updatable = false)
    private String registeredByProcess;

    @Column(name = "reversed_by_person_id")
    private Long reversedByPersonId;

    @Column(name = "reversed_by_process", length = 80)
    private String reversedByProcess;

    private Chargeback(Payment payment, NonNegativeMoney amount, LocalDate chargedBackOn, String gatewayReference, StatusChangeActor actor) {
        this.paymentId = Objects.requireNonNull(payment.getId(), "paymentId");
        this.studentCurrentAccountId = payment.getStudentCurrentAccountId();
        this.amount = Objects.requireNonNull(amount, "amount");
        this.chargedBackOn = Objects.requireNonNull(chargedBackOn, "chargedBackOn");
        this.gatewayReference = gatewayReference;
        this.registeredAt = ApplicationTime.now();
        this.registeredByPersonId = actor.personId();
        this.registeredByProcess = actor.processName();
    }

    public static Chargeback registerFor(Payment payment, NonNegativeMoney amount, LocalDate chargedBackOn, String gatewayReference, StatusChangeActor actor) {
        Objects.requireNonNull(actor, "actor");
        if (amount.value().isZero()) {
            throw new IllegalArgumentException("A chargeback amount must be greater than zero");
        }
        return new Chargeback(payment, amount, chargedBackOn, gatewayReference, actor);
    }

    public void reverseOn(LocalDate reversalDate, StatusChangeActor actor) {
        Objects.requireNonNull(reversalDate, "reversalDate");
        Objects.requireNonNull(actor, "actor");
        if (isReversed()) {
            throw new ChargebackAlreadyReversedException();
        }
        if (reversalDate.isBefore(chargedBackOn)) {
            throw new IllegalArgumentException("A chargeback cannot be reversed before it was registered");
        }
        this.reversedOn = reversalDate;
        this.reversedByPersonId = actor.personId();
        this.reversedByProcess = actor.processName();
    }

    public boolean isReversed() {
        return reversedOn != null;
    }
}
