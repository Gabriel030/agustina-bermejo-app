package com.red_escuela.backend.billing.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import com.red_escuela.backend.billing.exception.InvalidSurchargePercentageException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import com.red_escuela.backend.identity.entity.Person;

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

    @Column(name = "suspension_after_days", nullable = false)
    private Integer suspensionAfterDays;

    @Column(name = "suspension_after_invoice_count", nullable = false)
    private Integer suspensionAfterInvoiceCount;

    @Column(name = "withdrawal_after_days", nullable = false)
    private Integer withdrawalAfterDays;

    @Column(name = "withdrawal_after_invoice_count", nullable = false)
    private Integer withdrawalAfterInvoiceCount;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by_id", nullable = false)
    private Person createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
    }

    public void setSurchargePercentage(BigDecimal surchargePercentage) {
        if (surchargePercentage == null || surchargePercentage.signum() < 0 || surchargePercentage.compareTo(BigDecimal.ONE) > 0) {
            throw new InvalidSurchargePercentageException(surchargePercentage);
        }
        this.surchargePercentage = surchargePercentage;
    }
}
