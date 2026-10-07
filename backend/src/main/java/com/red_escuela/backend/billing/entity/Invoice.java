package com.red_escuela.backend.billing.entity;

import com.red_escuela.backend.shared.time.ApplicationTime;
import jakarta.persistence.*;
import com.red_escuela.backend.shared.domain.CurrencyMismatchException;
import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.shared.domain.MonetaryAdjustment;
import lombok.Getter;
import com.red_escuela.backend.shared.domain.Money;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import com.red_escuela.backend.enrollment.entity.CourseEnrollment;
import com.red_escuela.backend.evaluation.entity.ExamBoardRegistration;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.academicstructure.entity.Subject;
import com.red_escuela.backend.billing.invoice.domain.FeeType;
import com.red_escuela.backend.billing.invoice.domain.InvoiceIssuingReason;

@Getter
@Setter
@Entity(name = "LegacyInvoice")
@Table(name = "legacy_invoice")
public class Invoice {
    @Id
    @Column(name = "id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    @Column(name = "version")
    private Long version;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "course_enrollment_id")
    private CourseEnrollment courseEnrollment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "program_enrollment_id")
    private ProgramEnrollment programEnrollment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_plan_selection_id")
    private PaymentPlanSelection paymentPlanSelection;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subject_id")
    private Subject subject;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "exam_board_registration_id")
    private ExamBoardRegistration examBoardRegistration;

    @Column(name = "description", length = 500)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "fee_type", nullable = false)
    private FeeType feeType;

    @Column(name = "period", nullable = false)
    private YearMonth period;

    @Column(name = "template_id")
    private Long templateId;

    @Column(name = "enrollment_fee_slot")
    private Integer enrollmentFeeSlot;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "amount", column = @Column(name = "base_amount", nullable = false)),
            @AttributeOverride(name = "currency", column = @Column(name = "base_amount_currency", nullable = false))
    })
    private Money baseAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private InvoiceStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "issue_reason", nullable = false)
    private InvoiceIssuingReason issueReason = InvoiceIssuingReason.LEGACY;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "price_revision_id")
    private PriceRevision priceRevision;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cancelled_by_id")
    private Person cancelledBy;

    @Column(name = "cancelled_reason", length = 500)
    private String cancelledReason;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    @OneToMany(mappedBy = "invoice", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    private List<InvoiceAdjustment> adjustments = new ArrayList<>();

    @OneToMany(mappedBy = "invoice", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PaymentAllocation> allocations = new ArrayList<>();

    public static Invoice issue(InvoiceDraft draft, LocalDate issuedOn) {
        if (draft.dueDate().isBefore(issuedOn)) {
            throw new IllegalArgumentException("Due date " + draft.dueDate() + " is before issue date " + issuedOn);
        }
        Invoice invoice = new Invoice();
        invoice.programEnrollment = draft.programEnrollment();
        invoice.courseEnrollment = draft.courseEnrollment();
        invoice.paymentPlanSelection = draft.selection();
        invoice.feeType = draft.feeType();
        invoice.period = draft.period();
        invoice.dueDate = draft.dueDate();
        invoice.baseAmount = draft.baseAmount();
        invoice.issueReason = draft.reason();
        invoice.priceRevision = draft.priceRevision();
        invoice.templateId = draft.templateId();
        invoice.enrollmentFeeSlot = draft.enrollmentFeeSlot();
        invoice.description = draft.description();
        invoice.subject = draft.subject();
        invoice.examBoardRegistration = draft.examBoardRegistration();
        invoice.status = InvoiceStatus.PENDING;
        return invoice;
    }

    public void attachToEnrollment(ProgramEnrollment enrollment) {
        requireAttachable(InvoiceIssuingReason.PROGRAM_ADMISSION);
        if (enrollment.getProgramCohort() == null || templateId == null
                || !templateId.equals(enrollment.getProgramCohort().getTemplate().getId())) {
            throw new com.red_escuela.backend.billing.exception.InvoiceAttachmentMismatchException("invoice " + id + " / program enrollment " + enrollment.getId());
        }
        this.programEnrollment = enrollment;
    }

    public void attachToEnrollment(CourseEnrollment enrollment) {
        requireAttachable(InvoiceIssuingReason.COURSE_ADMISSION);
        this.courseEnrollment = enrollment;
    }

    private void requireAttachable(InvoiceIssuingReason expected) {
        if (issueReason != expected) {
            throw new com.red_escuela.backend.billing.exception.InvoiceAttachmentMismatchException("invoice " + id + " has reason " + issueReason);
        }
        if (programEnrollment != null || courseEnrollment != null) {
            throw new com.red_escuela.backend.billing.exception.InvoiceAlreadyAttachedException("invoice " + id);
        }
    }

    @PrePersist
    void onCreate() {
        LocalDateTime now = ApplicationTime.now();
        if (this.createdAt == null) {
            this.createdAt = now;
        }
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = ApplicationTime.now();
    }

    public Money getAmountDue() {
        Money net = baseAmount.plus(adjustments.stream()
                .map(InvoiceAdjustment::signedAmount)
                .reduce(Money.zero(currency()), Money::plus));
        return net.isNegative() ? Money.zero(currency()) : net;
    }

    public Money getRemainingBalance() {
        Money applied = allocations.stream()
                .map(PaymentAllocation::getAmountApplied)
                .reduce(Money.zero(currency()), Money::plus);
        return getAmountDue().minus(applied);
    }

    public Currency currency() {
        return baseAmount.currency();
    }

    public boolean isPayable() {
        return status == InvoiceStatus.PENDING || status == InvoiceStatus.OVERDUE;
    }

    public PaymentAllocation registerPayment(Money amountApplied, Payment payment) {
        if (amountApplied.currency() != currency()) {
            throw new CurrencyMismatchException(currency(), amountApplied.currency());
        }
        PaymentAllocation allocation = new PaymentAllocation();
        allocation.setInvoice(this);
        allocation.setPayment(payment);
        allocation.setAmountApplied(amountApplied);
        allocations.add(allocation);
        if (getRemainingBalance().isZero()) {
            status = InvoiceStatus.PAID;
        }
        return allocation;
    }

    public boolean markOverdue(MonetaryAdjustment surchargeValue, String reason) {
        if (status != InvoiceStatus.PENDING) {
            return false;
        }
        status = InvoiceStatus.OVERDUE;
        applyAdjustment(InvoiceAdjustmentType.SURCHARGE, surchargeValue, reason, null);
        return true;
    }

    public void cancel(Person cancelledBy, String reason) {
        if (status == InvoiceStatus.PAID || status == InvoiceStatus.WAIVED || status == InvoiceStatus.CANCELLED) {
            throw new IllegalStateException("Invoice " + id + " en estado " + status + " no puede cancelarse");
        }
        status = InvoiceStatus.CANCELLED;
        this.cancelledBy = cancelledBy;
        this.cancelledReason = reason;
        this.cancelledAt = ApplicationTime.now();
    }

    public void applyAdjustment(InvoiceAdjustmentType type, MonetaryAdjustment value, String reason, Person appliedBy) {
        applyAdjustment(type, value, reason, appliedBy, null);
    }

    public void applyAdjustment(InvoiceAdjustmentType type, MonetaryAdjustment value, String reason, Person appliedBy, PromotionBenefit promotionBenefit) {
        Money base = (type == InvoiceAdjustmentType.SURCHARGE) ? getRemainingBalance() : this.baseAmount;
        InvoiceAdjustment adjustment = new InvoiceAdjustment();
        adjustment.setInvoice(this);
        adjustment.setType(type);
        adjustment.setValue(value);
        adjustment.setResolvedBase(base);
        adjustment.setReason(reason);
        adjustment.setAppliedBy(appliedBy);
        adjustment.setAppliedAt(ApplicationTime.now());
        adjustment.setPromotionBenefit(promotionBenefit);
        adjustments.add(adjustment);
        if (getRemainingBalance().isZero()
                && (status == InvoiceStatus.PENDING || status == InvoiceStatus.OVERDUE)) {
            status = InvoiceStatus.WAIVED;
        }
    }
}
