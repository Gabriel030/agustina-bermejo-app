package com.red_escuela.backend.billing.entity;

import com.red_escuela.backend.shared.time.ApplicationTime;
import jakarta.persistence.*;
import com.red_escuela.backend.shared.domain.Percentage;
import com.red_escuela.backend.shared.persistence.PercentageConverter;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;


import java.time.LocalDate;
import java.time.LocalDateTime;
import com.red_escuela.backend.identity.entity.Person;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "late_payment_settings")
public class LatePaymentSettings {
    @Id
    @Column(name = "id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "grace_period_days", nullable = false)
    private Integer gracePeriodDays;

    @Convert(converter = PercentageConverter.class)
    @Column(name = "surcharge_percentage", nullable = false)
    private Percentage surchargeRate;

    @Column(name = "suspension_after_days", nullable = false)
    private Integer suspensionAfterDays;

    @Column(name = "suspension_after_invoice_count", nullable = false)
    private Integer suspensionAfterInvoiceCount;

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
            this.createdAt = ApplicationTime.now();
        }
    }
}
