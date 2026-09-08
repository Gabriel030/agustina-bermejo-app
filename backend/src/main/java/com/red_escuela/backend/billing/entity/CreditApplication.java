package com.red_escuela.backend.billing.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// Tabla de asociación "un CreditNote reparte contra varios comprobantes", mismo patrón que
// PaymentAllocation (Fase 6.2) para un origen de plata distinto (crédito interno, no un pago
// real) — append-only, sin updatedAt.
@Getter
@Setter
@Entity
@Table(name = "credit_application")
public class CreditApplication {

    @Id
    @Column(name = "id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "credit_note_id", nullable = false)
    private CreditNote creditNote;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "invoice_id", nullable = false)
    private Invoice invoice;

    // Nullable: una nota de crédito retroactiva (RETROACTIVE_SCHOLARSHIP) se aplica contra una
    // Invoice ya PAID sin que exista un Payment nuevo asociado a ese crédito.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_id")
    private Payment payment;

    @Column(name = "amount_applied", nullable = false)
    private BigDecimal amountApplied;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
    }
}
