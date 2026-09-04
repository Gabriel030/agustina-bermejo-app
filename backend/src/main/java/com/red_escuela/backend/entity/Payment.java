package com.red_escuela.backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

// Aggregate Root propio (junto a Invoice) — un Payment puede saldar varias Invoice a la vez vía
// PaymentAllocation (patrón contable estándar), nunca al revés.
@Getter
@Setter
@Entity
@Table(name = "payment")
public class Payment {

    @Id
    @Column(name = "id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    @Column(name = "version")
    private Long version;

    @Column(name = "amount", nullable = false)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "method", nullable = false)
    private PaymentMethod method;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "registered_by_id", nullable = false)
    private Person registeredBy;

    @Column(name = "paid_at", nullable = false)
    private LocalDateTime paidAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private PaymentStatus status;

    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;

    // Id de transacción de Mercado Pago — se completa recién en Fase 6.9.
    @Column(name = "external_reference")
    private String externalReference;

    // Informativo, nunca dispara lógica de facturación: la cantidad de cuotas de tarjeta que
    // eligió el alumno en el checkout de Mercado Pago no tiene relación con Invoice ni
    // PaymentPlanSelection — el merchant cobra el total igual.
    @Column(name = "card_installments")
    private Integer cardInstallments;

    @Column(name = "notes", length = 500)
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "payment", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
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
}
