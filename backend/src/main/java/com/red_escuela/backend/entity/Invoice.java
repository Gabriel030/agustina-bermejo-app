package com.red_escuela.backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

// Aggregate Root — Invoice is the root; InvoiceAdjustment (this phase) and PaymentAllocation
// (Fase 6.2) are children only ever modified through Invoice's own methods (applyAdjustment,
// and registerPayment in Fase 6.2), never by setting status from the outside.
//
// Deviation from Fase 6.1 spec: no subscription_id column/FK yet (the spec declares it already,
// always null until Fase 6.10 adds Subscription). Adding a column that references a table that
// doesn't exist yet — and stays permanently null in the meantime — is exactly the kind of
// speculative column this project avoids; Fase 6.10 adds it together with the Subscription
// entity itself. Same applies to PaymentPlanOption/PaymentPlanSelection.subscription_id, and to
// Invoice.subject_id/exam_board_registration_id (Fase 6.7, ad-hoc invoices).
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

    // Nullable — ad-hoc invoices (Fase 6.7) don't come from any plan.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_plan_selection_id")
    private PaymentPlanSelection paymentPlanSelection;

    @Enumerated(EnumType.STRING)
    @Column(name = "fee_type", nullable = false)
    private FeeType feeType;

    @Column(name = "period", nullable = false)
    private YearMonth period;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    // Snapshot of the PriceRevision at generation time.
    @Column(name = "base_amount", nullable = false)
    private BigDecimal baseAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private InvoiceStatus status;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    // Cancellation audit — only used starting a later phase (voluntary withdrawal), but the
    // columns are born with the rest of the table.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cancelled_by_id")
    private Person cancelledBy;

    @Column(name = "cancelled_reason", length = 500)
    private String cancelledReason;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    @OneToMany(mappedBy = "invoice", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    private List<InvoiceAdjustment> adjustments = new ArrayList<>();

    // PaymentAllocation is completed in Fase 6.2 — this relation is declared already, stays
    // empty until then.
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

    // Corrección 2026-09-04 (auditoría): el comentario de cabecera de esta clase ya prometía este
    // método ("PaymentAllocation... solo se modifica a través de los métodos propios de Invoice
    // — applyAdjustment, y registerPayment") pero nunca se había escrito — PaymentService seteaba
    // status=PAID directo desde afuera, violando la invariante de Aggregate Root que esta misma
    // clase declara.
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

    // Corrección 2026-09-04 (auditoría, segunda pasada): InvoiceOverdueRowProcessor seteaba
    // status=OVERDUE directo desde afuera, el único otro lugar (junto al ya corregido PAID) donde
    // se violaba la invariante de Aggregate Root que esta clase declara — la transición a OVERDUE
    // y el recargo automático que la acompaña son un único paso de dominio, no dos mutaciones
    // independientes desde el service.
    public void markOverdue(MonetaryAdjustmentValue surchargeValue, String reason) {
        status = InvoiceStatus.OVERDUE;
        applyAdjustment(InvoiceAdjustmentType.SURCHARGE, surchargeValue, reason, null);
    }

    public void applyAdjustment(InvoiceAdjustmentType type, MonetaryAdjustmentValue value, String reason, Person appliedBy) {
        // SCHOLARSHIP/DISCOUNT resolve against baseAmount (order-independent from each other).
        // SURCHARGE resolves against getRemainingBalance() computed BEFORE adding it — a partial
        // payment made before falling into arrears is also subtracted from the surcharge base.
        BigDecimal base = (type == InvoiceAdjustmentType.SURCHARGE) ? getRemainingBalance() : this.baseAmount;
        InvoiceAdjustment adjustment = new InvoiceAdjustment();
        adjustment.setInvoice(this);
        adjustment.setType(type);
        adjustment.setValue(value);
        adjustment.setResolvedBase(base);
        adjustment.setReason(reason);
        adjustment.setAppliedBy(appliedBy);
        adjustment.setAppliedAt(LocalDateTime.now());
        adjustments.add(adjustment);
        // Any adjustment that leaves getRemainingBalance() at zero transitions to WAIVED only.
        if (getRemainingBalance().compareTo(BigDecimal.ZERO) == 0 && status == InvoiceStatus.PENDING) {
            status = InvoiceStatus.WAIVED;
        }
    }
}
