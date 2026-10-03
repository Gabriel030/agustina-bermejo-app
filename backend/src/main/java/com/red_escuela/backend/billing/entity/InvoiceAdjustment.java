package com.red_escuela.backend.billing.entity;

import jakarta.persistence.*;
import com.red_escuela.backend.shared.domain.MonetaryAdjustment;
import com.red_escuela.backend.shared.persistence.MonetaryAdjustmentConverter;
import lombok.Getter;
import com.red_escuela.backend.shared.domain.Money;
import lombok.Setter;
import org.hibernate.annotations.ColumnTransformer;

import java.time.LocalDateTime;
import com.red_escuela.backend.identity.entity.Person;

@Getter
@Setter
@Entity
@Table(name = "invoice_adjustment")
public class InvoiceAdjustment {
    @Id
    @Column(name = "id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "invoice_id", nullable = false)
    private Invoice invoice;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private InvoiceAdjustmentType type;

    @Convert(converter = MonetaryAdjustmentConverter.class)
    @ColumnTransformer(write = "?::jsonb")
    @Column(name = "value", nullable = false, columnDefinition = "jsonb")
    private MonetaryAdjustment value;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "amount", column = @Column(name = "resolved_base", nullable = false)),
            @AttributeOverride(name = "currency", column = @Column(name = "resolved_base_currency", nullable = false))
    })
    private Money resolvedBase;

    @Column(name = "reason", length = 500, nullable = false)
    private String reason;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "applied_by_id")
    private Person appliedBy;

    @Column(name = "applied_at", nullable = false)
    private LocalDateTime appliedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "promotion_benefit_id")
    private PromotionBenefit promotionBenefit;

    public Money signedAmount() {
        Money magnitude = value.applyTo(resolvedBase);
        if (type == InvoiceAdjustmentType.SURCHARGE) {
            return magnitude;
        }
        return Money.zero(magnitude.currency()).minus(magnitude);
    }
}
