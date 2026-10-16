package com.red_escuela.backend.billing.pricing.domain;

import com.red_escuela.backend.billing.invoice.domain.FeeType;
import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.shared.domain.NonNegativeMoney;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
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

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "price")
public class Price {
    private static final Set<PriceVersionStatus> RESOLVABLE = Set.of(PriceVersionStatus.SCHEDULED, PriceVersionStatus.EFFECTIVE, PriceVersionStatus.SUPERSEDED);
    private static final String SAME_DATE_REPLACEMENT_REASON = "Reemplazada por otra versión con la misma fecha de inicio.";
    private static final Comparator<PriceVersion> RESOLUTION_ORDER = Comparator
            .comparing(PriceVersion::getEffectiveFrom)
            .thenComparing(PriceVersion::getDecidedAt, Comparator.nullsFirst(Comparator.naturalOrder()))
            .thenComparing(PriceVersion::getId, Comparator.nullsLast(Comparator.naturalOrder()));

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "program_id", updatable = false)
    private Long programId;

    @Column(name = "course_id", updatable = false)
    private Long courseId;

    @Enumerated(EnumType.STRING)
    @Column(name = "fee_type", nullable = false, updatable = false)
    private FeeType feeType;

    @Enumerated(EnumType.STRING)
    @Column(name = "currency", nullable = false, updatable = false)
    private Currency currency;

    @Column(name = "description", nullable = false)
    private String description;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @OneToMany(mappedBy = "price", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id asc")
    private List<PriceVersion> versions = new ArrayList<>();

    public static Price forProgram(Long programId, FeeType feeType, Currency currency, String description) {
        Price price = create(feeType, currency, description);
        price.programId = Objects.requireNonNull(programId, "programId");
        return price;
    }

    public static Price forCourse(Long courseId, FeeType feeType, Currency currency, String description) {
        Price price = create(feeType, currency, description);
        price.courseId = Objects.requireNonNull(courseId, "courseId");
        return price;
    }

    public PriceVersion publishPriceVersion(NonNegativeMoney baseAmount, MarketingDiscount discount, LocalDate effectiveFrom, String changeReason,
                                            Long publisherId, boolean initialVersion, LocalDate today, Instant now) {
        requireOwnCurrency(baseAmount);
        requireDiscountFitsPrice(baseAmount, discount);
        requireNotRetroactive(effectiveFrom, today);
        Long previousId = findVersionEffectiveOn(today).map(PriceVersion::getId).orElse(null);
        if (!effectiveFrom.isAfter(today)) {
            supersedeEffectiveVersions(publisherId, now);
        }
        PriceVersion version = PriceVersion.publish(this, baseAmount, discount, effectiveFrom, changeReason, publisherId, initialVersion, today, previousId, now);
        versions.add(version);
        return version;
    }

    public PriceVersion proposePriceVersion(NonNegativeMoney baseAmount, MarketingDiscount discount, LocalDate effectiveFrom, String changeReason,
                                            Long proposerId, Long authorizationRequestId, boolean initialVersion, LocalDate today, Instant now) {
        requireOwnCurrency(baseAmount);
        requireDiscountFitsPrice(baseAmount, discount);
        requireNotRetroactive(effectiveFrom, today);
        Long previousId = findVersionEffectiveOn(today).map(PriceVersion::getId).orElse(null);
        PriceVersion version = PriceVersion.propose(this, baseAmount, discount, effectiveFrom, changeReason, proposerId, authorizationRequestId, initialVersion, previousId, now);
        versions.add(version);
        return version;
    }

    public void approveProposedVersion(PriceVersion version, Long deciderId, String decisionReason, LocalDate today, Instant now) {
        requireOwnVersion(version);
        if (version.isOpenProposal() && !version.getEffectiveFrom().isAfter(today)) {
            supersedeEffectiveVersions(deciderId, now);
        }
        version.approve(deciderId, decisionReason, today, now);
    }

    public void rejectProposedVersion(PriceVersion version, Long deciderId, String decisionReason, Instant now) {
        requireOwnVersion(version);
        version.reject(deciderId, decisionReason, now);
    }

    public void withdrawProposedVersion(PriceVersion version, Long requesterId, Instant now) {
        requireOwnVersion(version);
        version.withdraw(requesterId, now);
    }

    public void expireProposedVersion(PriceVersion version, Instant now) {
        requireOwnVersion(version);
        version.expire(now);
    }

    public void cancelScheduledVersion(PriceVersion version, Long directorId, String cancellationReason, Instant now) {
        requireOwnVersion(version);
        version.cancel(directorId, cancellationReason, now);
    }

    public void activateScheduledVersion(PriceVersion version, Instant now) {
        requireOwnVersion(version);
        versions.stream().filter(candidate -> candidate.getStatus() == PriceVersionStatus.EFFECTIVE).forEach(candidate -> candidate.supersedeByActivation(now));
        version.reachStart(now);
    }

    public void makeRoomForVersionStartingOn(LocalDate effectiveFrom, LocalDate today, Long causedById, Instant now) {
        boolean alreadyInForce = !effectiveFrom.isAfter(today);
        versions.stream()
                .filter(version -> version.getStatus() == PriceVersionStatus.SCHEDULED && version.getEffectiveFrom().equals(effectiveFrom))
                .forEach(version -> {
                    if (alreadyInForce) {
                        version.reachStartCausedBy(causedById, now);
                    } else {
                        version.cancel(causedById, SAME_DATE_REPLACEMENT_REASON, now);
                    }
                });
        if (!effectiveFrom.isAfter(today)) {
            supersedeEffectiveVersions(causedById, now);
        }
    }

    public List<PriceVersion> activateDueVersions(LocalDate today, Instant now) {
        List<PriceVersion> due = versions.stream()
                .filter(version -> version.getStatus() == PriceVersionStatus.SCHEDULED && !version.getEffectiveFrom().isAfter(today))
                .sorted(RESOLUTION_ORDER)
                .toList();
        due.forEach(version -> version.reachStart(now));
        Optional<PriceVersion> newest = versions.stream().filter(version -> version.getStatus() == PriceVersionStatus.EFFECTIVE).max(RESOLUTION_ORDER);
        versions.stream()
                .filter(version -> version.getStatus() == PriceVersionStatus.EFFECTIVE && !version.equals(newest.orElse(null)))
                .forEach(version -> version.supersedeByActivation(now));
        return due;
    }

    public void retireEffectiveVersions(Long causedById, Instant now) {
        supersedeEffectiveVersions(causedById, now);
    }

    public Optional<PriceVersion> findVersionEffectiveOn(LocalDate date) {
        return versions.stream()
                .filter(version -> RESOLVABLE.contains(version.getStatus()))
                .filter(version -> !version.getEffectiveFrom().isAfter(date))
                .max(RESOLUTION_ORDER);
    }

    public Optional<PriceVersion> findNextScheduledVersionAfter(LocalDate date) {
        return versions.stream()
                .filter(version -> version.getStatus() == PriceVersionStatus.SCHEDULED && version.getEffectiveFrom().isAfter(date))
                .min(RESOLUTION_ORDER);
    }

    public Optional<PriceVersion> findOpenProposal() {
        return versions.stream().filter(PriceVersion::isOpenProposal).max(RESOLUTION_ORDER);
    }

    public List<PriceVersion> versionsNewestFirst() {
        return versions.stream().sorted(RESOLUTION_ORDER.reversed()).toList();
    }

    public List<PriceVersion> getVersions() {
        return List.copyOf(versions);
    }

    private static Price create(FeeType feeType, Currency currency, String description) {
        Price price = new Price();
        price.feeType = Objects.requireNonNull(feeType, "feeType");
        price.currency = Objects.requireNonNull(currency, "currency");
        price.description = Objects.requireNonNull(description, "description");
        return price;
    }

    private void supersedeEffectiveVersions(Long causedById, Instant now) {
        versions.stream().filter(version -> version.getStatus() == PriceVersionStatus.EFFECTIVE).forEach(version -> version.supersedeCausedBy(causedById, now));
    }

    private void requireDiscountFitsPrice(NonNegativeMoney baseAmount, MarketingDiscount discount) {
        if (discount == null || discount.type() != MarketingDiscountType.FIXED) {
            return;
        }
        NonNegativeMoney fixed = discount.fixedAmount();
        if (fixed.value().currency() != currency) {
            throw new IllegalArgumentException("A price in " + currency + " cannot take a discount in " + fixed.value().currency());
        }
        if (fixed.value().isGreaterThan(baseAmount.value())) {
            throw new IllegalArgumentException("A fixed marketing discount cannot exceed the base amount");
        }
    }

    private void requireOwnCurrency(NonNegativeMoney baseAmount) {
        if (baseAmount.value().currency() != currency) {
            throw new IllegalArgumentException("A price in " + currency + " cannot take a version in " + baseAmount.value().currency());
        }
    }

    private static void requireNotRetroactive(LocalDate effectiveFrom, LocalDate today) {
        if (effectiveFrom.isBefore(today)) {
            throw new IllegalArgumentException("A price version cannot start before today");
        }
    }

    private void requireOwnVersion(PriceVersion version) {
        if (!versions.contains(version)) {
            throw new IllegalArgumentException("The version does not belong to this price");
        }
    }
}
