package com.red_escuela.backend.billing.invoice.domain;

import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.shared.domain.MonetaryAdjustment;
import com.red_escuela.backend.shared.domain.NonNegativeMoney;
import com.red_escuela.backend.shared.persistence.MonetaryAdjustmentConverter;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnTransformer;
import org.hibernate.annotations.Immutable;

import java.time.LocalDateTime;
import java.util.Optional;

@Entity
@Table(name = "invoice_discount")
@Immutable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InvoiceDiscount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @OneToOne(optional = false)
    @JoinColumn(name = "invoice_line_id", nullable = false)
    private InvoiceLine invoiceLine;

    @Column(name = "student_discount_id")
    private Long studentDiscountId;

    @Enumerated(EnumType.STRING)
    @Column(name = "discount_type", nullable = false)
    private DiscountType discountType;

    @Column(name = "concept", nullable = false, length = 200)
    private String concept;

    @Enumerated(EnumType.STRING)
    @Column(name = "currency", nullable = false)
    private Currency currency;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "value.amount", column = @Column(name = "base_amount", nullable = false)),
            @AttributeOverride(name = "value.currency", column = @Column(name = "base_amount_currency", nullable = false))})
    private NonNegativeMoney baseAmount;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "value.amount", column = @Column(name = "discount_amount", nullable = false)),
            @AttributeOverride(name = "value.currency", column = @Column(name = "discount_amount_currency", nullable = false))})
    private NonNegativeMoney discountAmount;

    @Column(name = "installment_number")
    private Integer installmentNumber;

    @Column(name = "installments_total")
    private Integer installmentsTotal;

    @Column(name = "source_installment_id")
    private Long sourceInstallmentId;

    @Convert(converter = MonetaryAdjustmentConverter.class)
    @ColumnTransformer(write = "?::jsonb")
    @Column(name = "calculation", columnDefinition = "jsonb")
    private MonetaryAdjustment calculation;

    @Column(name = "consumption_released_at", insertable = false, updatable = false)
    private LocalDateTime consumptionReleasedAt;

    InvoiceDiscount(InvoiceLine invoiceLine, InvoiceDiscountDraft draft) {
        this.invoiceLine = invoiceLine;
        this.studentDiscountId = draft.studentDiscountId();
        this.discountType = draft.discountType();
        this.concept = draft.concept();
        this.currency = draft.discountAmount().value().currency();
        this.baseAmount = draft.baseAmount();
        this.discountAmount = draft.discountAmount();
        this.installmentNumber = draft.installmentNumber();
        this.installmentsTotal = draft.installmentsTotal();
        this.sourceInstallmentId = draft.sourceInstallmentId();
        this.calculation = draft.calculation();
    }

    public Optional<String> describeInstallmentProgress() {
        if (installmentNumber == null) {
            return Optional.empty();
        }
        if (installmentsTotal == null) {
            return Optional.of(String.valueOf(installmentNumber));
        }
        return Optional.of(installmentNumber + " de " + installmentsTotal);
    }
}
