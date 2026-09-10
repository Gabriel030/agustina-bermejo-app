package com.red_escuela.backend.billing.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "promotion_redemption")
public class PromotionRedemption {

    @Id
    @Column(name = "id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "promotion_id", nullable = false)
    private Promotion promotion;

    // Atada a UNA PaymentPlanSelection puntual, no al enrollment — si esa selección se cierra (el
    // alumno cambia de plan), la promoción no se traslada a la selección nueva (decisión explícita
    // del dueño del producto, ver Fase 6.5 en Obsidian).
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payment_plan_selection_id", nullable = false, unique = true)
    private PaymentPlanSelection paymentPlanSelection;

    @Column(name = "redeemed_at", nullable = false)
    private LocalDateTime redeemedAt;

    @PrePersist
    void onCreate() {
        if (this.redeemedAt == null) {
            this.redeemedAt = LocalDateTime.now();
        }
    }
}
