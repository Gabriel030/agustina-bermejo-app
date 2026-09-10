package com.red_escuela.backend.billing.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.time.YearMonth;
import com.red_escuela.backend.enrollment.entity.CourseEnrollment;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;

@Getter
@Setter
@Entity
@Table(name = "payment_plan_selection")
public class PaymentPlanSelection {

    @Id
    @Column(name = "id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Corrección 2026-09-04 (auditoría): sin esto, cerrar la selección "abierta" anterior
    // (PaymentPlanSelectionService, al setear effectiveTo) dependía únicamente de que dos
    // requests concurrentes escribieran el mismo valor determinístico por casualidad — mismo
    // patrón fragil de "protegido de rebote por un unique index en otra fila" que ya causó un bug
    // real (uk_payment_allocation) en otra entidad de este mismo dominio.
    @Version
    @Column(name = "version")
    private Long version;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "course_enrollment_id")
    private CourseEnrollment courseEnrollment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "program_enrollment_id")
    private ProgramEnrollment programEnrollment;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payment_plan_option_id", nullable = false)
    private PaymentPlanOption paymentPlanOption;

    @Column(name = "effective_from", nullable = false)
    private YearMonth effectiveFrom;

    // null = still open, in effect until another selection replaces it.
    @Column(name = "effective_to")
    private YearMonth effectiveTo;

    // Only set if paymentPlanOption.cadence = UPFRONT.
    @Column(name = "scope_months")
    private Integer scopeMonths;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by_id", nullable = false)
    private Person createdBy;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
    }
}
