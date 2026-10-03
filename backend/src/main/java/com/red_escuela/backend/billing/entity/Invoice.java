package com.red_escuela.backend.billing.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
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

@Getter
@Setter
@Entity
@Table(name = "invoice")
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

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Column(name = "base_amount", nullable = false)
    private BigDecimal baseAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private InvoiceStatus status;

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

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (this.createdAt == null) {
            this.createdAt = now;
        }
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public BigDecimal getAmountDue() {
        BigDecimal net = baseAmount.add(adjustments.stream()
                .map(InvoiceAdjustment::signedAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        return net.max(BigDecimal.ZERO);
    }

    public BigDecimal getRemainingBalance() {
        BigDecimal applied = allocations.stream()
                .map(PaymentAllocation::getAmountApplied)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return getAmountDue().subtract(applied);
    }

    public boolean isPayable() {
        return status == InvoiceStatus.PENDING || status == InvoiceStatus.OVERDUE;
    }

    public PaymentAllocation registerPayment(BigDecimal amountApplied, Payment payment) {
        PaymentAllocation allocation = new PaymentAllocation();
        allocation.setInvoice(this);
        allocation.setPayment(payment);
        allocation.setAmountApplied(amountApplied);
        allocations.add(allocation);
        if (getRemainingBalance().compareTo(BigDecimal.ZERO) == 0) {
            status = InvoiceStatus.PAID;
        }
        return allocation;
    }

    public void markOverdue(MonetaryAdjustmentValue surchargeValue, String reason) {
        status = InvoiceStatus.OVERDUE;
        applyAdjustment(InvoiceAdjustmentType.SURCHARGE, surchargeValue, reason, null);
    }

    public void cancel(Person cancelledBy, String reason) {
        if (status == InvoiceStatus.PAID || status == InvoiceStatus.WAIVED || status == InvoiceStatus.CANCELLED) {
            throw new IllegalStateException("Invoice " + id + " en estado " + status + " no puede cancelarse");
        }
        status = InvoiceStatus.CANCELLED;
        this.cancelledBy = cancelledBy;
        this.cancelledReason = reason;
        this.cancelledAt = LocalDateTime.now();
    }

    public void applyAdjustment(InvoiceAdjustmentType type, MonetaryAdjustmentValue value, String reason, Person appliedBy) {
        applyAdjustment(type, value, reason, appliedBy, null);
    }

    public void applyAdjustment(InvoiceAdjustmentType type, MonetaryAdjustmentValue value, String reason, Person appliedBy, PromotionBenefit promotionBenefit) {
        BigDecimal base = (type == InvoiceAdjustmentType.SURCHARGE) ? getRemainingBalance() : this.baseAmount;
        InvoiceAdjustment adjustment = new InvoiceAdjustment();
        adjustment.setInvoice(this);
        adjustment.setType(type);
        adjustment.setValue(value);
        adjustment.setResolvedBase(base);
        adjustment.setReason(reason);
        adjustment.setAppliedBy(appliedBy);
        adjustment.setAppliedAt(LocalDateTime.now());
        adjustment.setPromotionBenefit(promotionBenefit);
        adjustments.add(adjustment);
        if (getRemainingBalance().compareTo(BigDecimal.ZERO) == 0
                && (status == InvoiceStatus.PENDING || status == InvoiceStatus.OVERDUE)) {
            status = InvoiceStatus.WAIVED;
        }
    }
}
