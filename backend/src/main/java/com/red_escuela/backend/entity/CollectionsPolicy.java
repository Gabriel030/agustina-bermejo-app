package com.red_escuela.backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

// Value Object con vigencia temporal — mismo idioma que PriceRevision y PaymentPlanSelection
// (Fase 6.1): resolverVigente busca la de effectiveFrom más reciente que ya rige hoy.
@Getter
@Setter
@Entity
@Table(name = "collections_policy")
public class CollectionsPolicy {

    @Id
    @Column(name = "id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "grace_period_days", nullable = false)
    private Integer gracePeriodDays;

    @Column(name = "surcharge_percentage", nullable = false)
    private BigDecimal surchargePercentage;

    // Criterio doble para suspensión/baja (decisión 2026-09-03): SUSPENDED/WITHDRAWN_FOR_NONPAYMENT
    // se disparan por CUALQUIERA de las dos condiciones — antigüedad en días de la cuota OVERDUE
    // más vieja, o cantidad de cuotas OVERDUE simultáneas — la que se cumpla primero.
    @Column(name = "suspension_after_days", nullable = false)
    private Integer suspensionAfterDays;

    @Column(name = "suspension_after_invoice_count", nullable = false)
    private Integer suspensionAfterInvoiceCount;

    @Column(name = "withdrawal_after_days", nullable = false)
    private Integer withdrawalAfterDays;

    @Column(name = "withdrawal_after_invoice_count", nullable = false)
    private Integer withdrawalAfterInvoiceCount;

    // Desde cuándo RIGE — puede ser una fecha futura (una política cargada hoy con vigencia
    // dentro de un mes no afecta nada hasta esa fecha).
    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by_id", nullable = false)
    private Person createdBy;

    // Cuándo se CARGÓ, distinto de effectiveFrom.
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
    }
}
