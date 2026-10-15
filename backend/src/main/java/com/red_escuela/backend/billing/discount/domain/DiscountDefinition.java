package com.red_escuela.backend.billing.discount.domain;

import com.red_escuela.backend.billing.discount.exception.IllegalDiscountDefinitionTransitionException;
import com.red_escuela.backend.billing.invoice.domain.FeeType;
import com.red_escuela.backend.billing.invoice.domain.InvoiceType;
import com.red_escuela.backend.shared.domain.MonetaryAdjustment;
import com.red_escuela.backend.shared.domain.Money;
import com.red_escuela.backend.shared.domain.NonNegativeMoney;
import com.red_escuela.backend.shared.domain.statemachine.StatusChangeActor;
import com.red_escuela.backend.shared.persistence.MonetaryAdjustmentConverter;
import com.red_escuela.backend.shared.pricing.OfferingReference;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
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
import org.hibernate.annotations.ColumnTransformer;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "discount_definition")
public class DiscountDefinition {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, updatable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "discount_type", nullable = false, updatable = false)
    private DiscountDefinitionType discountType;

    @Enumerated(EnumType.STRING)
    @Column(name = "applicable_invoice_type", nullable = false, updatable = false)
    private InvoiceType applicableInvoiceType;

    @Convert(converter = MonetaryAdjustmentConverter.class)
    @ColumnTransformer(write = "?::jsonb")
    @Column(name = "calculation", columnDefinition = "jsonb", updatable = false)
    private MonetaryAdjustment calculation;

    @Column(name = "installments_total", updatable = false)
    private Integer installmentsTotal;

    @Column(name = "available_from", nullable = false, updatable = false)
    private LocalDate availableFrom;

    @Column(name = "available_until", updatable = false)
    private LocalDate availableUntil;

    @Column(name = "priority", nullable = false, updatable = false)
    private int priority;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "value.amount", column = @Column(name = "cap_per_invoice", updatable = false)),
            @AttributeOverride(name = "value.currency", column = @Column(name = "cap_per_invoice_currency", updatable = false))})
    private NonNegativeMoney capPerInvoice;

    @Column(name = "program_id", updatable = false)
    private Long programId;

    @Column(name = "program_cohort_id", updatable = false)
    private Long programCohortId;

    @Column(name = "course_id", updatable = false)
    private Long courseId;

    @Column(name = "enrollment_window_from", updatable = false)
    private LocalDate enrollmentWindowFrom;

    @Column(name = "enrollment_window_to", updatable = false)
    private LocalDate enrollmentWindowTo;

    @Column(name = "quota", updatable = false)
    private Integer quota;

    @Column(name = "quota_exhausted_notified_at")
    private Instant quotaExhaustedNotifiedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private DiscountDefinitionStatus status;

    @Column(name = "created_by_id", updatable = false)
    private Long createdById;

    @Column(name = "created_by_process", updatable = false)
    private String createdByProcess;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "direct_publication", nullable = false, updatable = false)
    private boolean directPublication;

    @Column(name = "authorization_request_id", updatable = false)
    private Long authorizationRequestId;

    @Column(name = "closed_by_id")
    private Long closedById;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Column(name = "closure_reason")
    private String closureReason;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @OneToMany(mappedBy = "discountDefinition", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id asc")
    private List<DiscountDefinitionStatusChange> statusChanges = new ArrayList<>();

    public static DiscountDefinition publishDiscountDefinition(DiscountDefinitionDraft draft, Long directorId, Instant now) {
        DiscountDefinition definition = create(draft, directorId, now);
        definition.directPublication = true;
        definition.moveTo(DiscountDefinitionEvent.DEFINITION_CREATED, StatusChangeActor.ofPerson(directorId), now, null);
        return definition;
    }

    public static DiscountDefinition createApprovedDiscountDefinition(DiscountDefinitionDraft draft, Long proposerId, Long authorizationRequestId, Instant now) {
        DiscountDefinition definition = create(draft, proposerId, now);
        definition.directPublication = false;
        definition.authorizationRequestId = authorizationRequestId;
        definition.moveTo(DiscountDefinitionEvent.DEFINITION_CREATED, StatusChangeActor.ofPerson(proposerId), now, null);
        return definition;
    }

    public void closeDiscountDefinition(String reason, StatusChangeActor actor, Instant now) {
        moveTo(DiscountDefinitionEvent.DEFINITION_CLOSED, actor, now, reason);
        this.closedById = actor.personId();
        this.closedAt = now;
        this.closureReason = reason;
    }

    public boolean isAvailableForAcquisitionOn(LocalDate date) {
        boolean withinAvailability = !date.isBefore(availableFrom) && (availableUntil == null || !date.isAfter(availableUntil));
        boolean withinEnrollmentWindow = (enrollmentWindowFrom == null || !date.isBefore(enrollmentWindowFrom))
                && (enrollmentWindowTo == null || !date.isAfter(enrollmentWindowTo));
        return status == DiscountDefinitionStatus.ACTIVE && withinAvailability && withinEnrollmentWindow;
    }

    public DiscountDefinitionAvailability availabilityOn(LocalDate date) {
        if (date.isBefore(availableFrom)) {
            return DiscountDefinitionAvailability.UPCOMING;
        }
        return availableUntil != null && date.isAfter(availableUntil) ? DiscountDefinitionAvailability.EXPIRED : DiscountDefinitionAvailability.AVAILABLE;
    }

    public Optional<OfferingReference> findPricedOffering() {
        if (programId != null) {
            return Optional.of(OfferingReference.ofProgram(programId));
        }
        return Optional.ofNullable(courseId).map(OfferingReference::ofCourse);
    }

    public Optional<FeeType> findPricedFeeType() {
        return switch (applicableInvoiceType) {
            case MONTHLY_INSTALLMENT -> Optional.of(FeeType.MONTHLY_INSTALLMENT);
            case ENROLLMENT_FEE -> Optional.of(FeeType.ENROLLMENT_FEE);
            case EXAM_FEE -> Optional.of(FeeType.EXAM_FEE);
            case RETAKEN_SUBJECT_FEE -> Optional.of(FeeType.RETAKEN_SUBJECT_FEE);
            case DEGREE_ISSUANCE_FEE -> Optional.of(FeeType.DEGREE_ISSUANCE_FEE);
            case ADMINISTRATIVE_FEE -> Optional.of(FeeType.ADMINISTRATIVE_FEE);
            case ANNUAL_UPFRONT, LATE_FEE -> Optional.empty();
        };
    }

    public Optional<NonNegativeMoney> estimateQuotaCost(NonNegativeMoney finalInstallmentPrice) {
        boolean singleCharge = applicableInvoiceType != InvoiceType.MONTHLY_INSTALLMENT;
        if (quota == null || calculation == null || (installmentsTotal == null && !singleCharge)) {
            return Optional.empty();
        }
        NonNegativeMoney savingPerInstallment = DiscountSavingCalculator.calculateSavingOnFinalPrice(calculation, finalInstallmentPrice, Optional.ofNullable(capPerInvoice));
        long usesPerPerson = singleCharge ? 1L : installmentsTotal;
        BigDecimal installmentsToCover = BigDecimal.valueOf(usesPerPerson * quota);
        Money total = Money.of(savingPerInstallment.value().amount().multiply(installmentsToCover), savingPerInstallment.value().currency());
        return Optional.of(NonNegativeMoney.of(total));
    }

    public List<DiscountDefinitionStatusChange> getStatusChanges() {
        return List.copyOf(statusChanges);
    }

    private static DiscountDefinition create(DiscountDefinitionDraft draft, Long authorId, Instant now) {
        DiscountDefinition definition = new DiscountDefinition();
        definition.name = draft.name().strip();
        definition.discountType = draft.discountType();
        definition.applicableInvoiceType = draft.applicableInvoiceType();
        definition.calculation = draft.calculation();
        definition.installmentsTotal = draft.installmentsTotal();
        definition.availableFrom = draft.availableFrom();
        definition.availableUntil = draft.availableUntil();
        definition.priority = draft.priority();
        definition.capPerInvoice = draft.capPerInvoice() == null ? null : NonNegativeMoney.of(draft.capPerInvoice());
        definition.programId = draft.programId();
        definition.programCohortId = draft.programCohortId();
        definition.courseId = draft.courseId();
        definition.enrollmentWindowFrom = draft.enrollmentWindowFrom();
        definition.enrollmentWindowTo = draft.enrollmentWindowTo();
        definition.quota = draft.quota();
        definition.createdById = authorId;
        definition.createdAt = now;
        return definition;
    }

    private void moveTo(DiscountDefinitionEvent event, StatusChangeActor actor, Instant now, String reason) {
        DiscountDefinitionStatus target = DiscountDefinitionStateMachine.findTargetStateFor(status, event)
                .orElseThrow(() -> new IllegalDiscountDefinitionTransitionException(status, event));
        statusChanges.add(new DiscountDefinitionStatusChange(this, status, target, event, actor, now, reason));
        this.status = target;
    }
}
