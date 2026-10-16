package com.red_escuela.backend.billing.pricing.domain;

import com.red_escuela.backend.shared.domain.NonNegativeMoney;
import com.red_escuela.backend.shared.domain.Percentage;
import com.red_escuela.backend.shared.persistence.PercentageConverter;
import com.red_escuela.backend.shared.domain.statemachine.StatusChangeActor;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "price_version")
public class PriceVersion {
    private static final String ACTIVATION_PROCESS = "PriceVersionActivation";
    private static final String EXPIRATION_PROCESS = "PriceProposalExpiration";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "price_id", nullable = false, updatable = false)
    private Price price;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "value.amount", column = @Column(name = "amount", nullable = false, updatable = false)),
            @AttributeOverride(name = "value.currency", column = @Column(name = "amount_currency", nullable = false, updatable = false))})
    private NonNegativeMoney baseAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "marketing_discount_type", updatable = false)
    private MarketingDiscountType marketingDiscountType;

    @Convert(converter = PercentageConverter.class)
    @Column(name = "marketing_discount_percentage", updatable = false)
    private Percentage marketingDiscountPercentage;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "value.amount", column = @Column(name = "marketing_discount_amount", updatable = false)),
            @AttributeOverride(name = "value.currency", column = @Column(name = "marketing_discount_amount_currency", updatable = false))})
    private NonNegativeMoney marketingDiscountFixedAmount;

    @Column(name = "effective_from", nullable = false, updatable = false)
    private LocalDate effectiveFrom;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private PriceVersionStatus status;

    @Column(name = "change_reason", nullable = false)
    private String changeReason;

    @Column(name = "proposed_by_id", nullable = false)
    private Long proposedById;

    @Column(name = "proposed_at", nullable = false)
    private Instant proposedAt;

    @Column(name = "decided_by_id")
    private Long decidedById;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "decision_reason")
    private String decisionReason;

    @Column(name = "direct_publication", nullable = false)
    private boolean directPublication;

    @Column(name = "initial_version", nullable = false)
    private boolean initialVersion;

    @Column(name = "authorization_request_id")
    private Long authorizationRequestId;

    @Column(name = "previous_version_id")
    private Long previousVersionId;

    @Column(name = "cancelled_by_id")
    private Long cancelledById;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "cancellation_reason")
    private String cancellationReason;

    @OneToMany(mappedBy = "priceVersion", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id asc")
    private List<PriceVersionStatusChange> statusChanges = new ArrayList<>();

    static PriceVersion propose(Price price, NonNegativeMoney baseAmount, MarketingDiscount discount, LocalDate effectiveFrom, String changeReason,
                                Long proposerId, Long authorizationRequestId, boolean initialVersion, Long previousVersionId, Instant now) {
        PriceVersion version = create(price, baseAmount, discount, effectiveFrom, changeReason, proposerId, initialVersion, previousVersionId, now);
        version.authorizationRequestId = authorizationRequestId;
        version.moveTo(PriceVersionEvent.PROPOSE, StatusChangeActor.ofPerson(proposerId), now, changeReason);
        return version;
    }

    static PriceVersion publish(Price price, NonNegativeMoney baseAmount, MarketingDiscount discount, LocalDate effectiveFrom, String changeReason,
                                Long publisherId, boolean initialVersion, LocalDate today, Long previousVersionId, Instant now) {
        PriceVersion version = create(price, baseAmount, discount, effectiveFrom, changeReason, publisherId, initialVersion, previousVersionId, now);
        version.directPublication = true;
        version.decidedById = publisherId;
        version.decidedAt = now;
        PriceVersionEvent event = effectiveFrom.isAfter(today) ? PriceVersionEvent.PUBLISH_FOR_FUTURE : PriceVersionEvent.PUBLISH_FOR_TODAY;
        version.moveTo(event, StatusChangeActor.ofPerson(publisherId), now, changeReason);
        return version;
    }

    void approve(Long deciderId, String reason, LocalDate today, Instant now) {
        PriceVersionEvent event = effectiveFrom.isAfter(today) ? PriceVersionEvent.APPROVE_FOR_FUTURE : PriceVersionEvent.APPROVE_FOR_TODAY;
        decide(event, deciderId, reason, now);
    }

    void reject(Long deciderId, String reason, Instant now) {
        decide(PriceVersionEvent.REJECT, deciderId, reason, now);
    }

    void withdraw(Long requesterId, Instant now) {
        moveTo(PriceVersionEvent.WITHDRAW, StatusChangeActor.ofPerson(requesterId), now, null);
    }

    void expire(Instant now) {
        moveTo(PriceVersionEvent.EXPIRE, StatusChangeActor.ofProcess(EXPIRATION_PROCESS), now, null);
    }

    void reachStart(Instant now) {
        moveTo(PriceVersionEvent.REACH_START, StatusChangeActor.ofProcess(ACTIVATION_PROCESS), now, null);
    }

    void reachStartCausedBy(Long personId, Instant now) {
        moveTo(PriceVersionEvent.REACH_START, StatusChangeActor.ofPerson(personId), now, null);
    }

    void supersedeByActivation(Instant now) {
        moveTo(PriceVersionEvent.SUPERSEDE, StatusChangeActor.ofProcess(ACTIVATION_PROCESS), now, null);
    }

    void supersedeCausedBy(Long personId, Instant now) {
        moveTo(PriceVersionEvent.SUPERSEDE, StatusChangeActor.ofPerson(personId), now, null);
    }

    void cancel(Long directorId, String reason, Instant now) {
        moveTo(PriceVersionEvent.CANCEL, StatusChangeActor.ofPerson(directorId), now, reason);
        this.cancelledById = directorId;
        this.cancelledAt = now;
        this.cancellationReason = reason;
    }

    public Optional<MarketingDiscount> marketingDiscount() {
        return marketingDiscountType == null ? Optional.empty() : Optional.of(new MarketingDiscount(marketingDiscountType, marketingDiscountPercentage, marketingDiscountFixedAmount));
    }

    public NonNegativeMoney marketingDiscountAmount() {
        return marketingDiscount().map(discount -> discount.discountOn(baseAmount)).orElse(NonNegativeMoney.zero(baseAmount.value().currency()));
    }

    public NonNegativeMoney finalAmount() {
        return baseAmount.minusNotBelowZero(marketingDiscountAmount());
    }

    public PriceVersionResolution resolution() {
        return new PriceVersionResolution(id, baseAmount, marketingDiscountAmount(), finalAmount());
    }

    public boolean isOpenProposal() {
        return status == PriceVersionStatus.PROPOSED;
    }

    public List<PriceVersionStatusChange> getStatusChanges() {
        return List.copyOf(statusChanges);
    }

    private static PriceVersion create(Price price, NonNegativeMoney baseAmount, MarketingDiscount discount, LocalDate effectiveFrom, String changeReason,
                                       Long authorId, boolean initialVersion, Long previousVersionId, Instant now) {
        PriceVersion version = new PriceVersion();
        version.price = price;
        version.baseAmount = baseAmount;
        if (discount != null) {
            version.marketingDiscountType = discount.type();
            version.marketingDiscountPercentage = discount.percentage();
            version.marketingDiscountFixedAmount = discount.fixedAmount();
        }
        version.effectiveFrom = effectiveFrom;
        version.changeReason = changeReason;
        version.proposedById = authorId;
        version.proposedAt = now;
        version.initialVersion = initialVersion;
        version.previousVersionId = previousVersionId;
        return version;
    }

    private void decide(PriceVersionEvent event, Long deciderId, String reason, Instant now) {
        moveTo(event, StatusChangeActor.ofPerson(deciderId), now, reason);
        this.decidedById = deciderId;
        this.decidedAt = now;
        this.decisionReason = reason;
    }

    private void moveTo(PriceVersionEvent event, StatusChangeActor actor, Instant now, String reason) {
        PriceVersionStatus target = PriceVersionStateMachine.findTargetStateFor(status, event)
                .orElseThrow(() -> new IllegalPriceVersionStatusTransitionException(status, event));
        statusChanges.add(new PriceVersionStatusChange(this, status, target, event, actor, now, reason));
        this.status = target;
    }
}
