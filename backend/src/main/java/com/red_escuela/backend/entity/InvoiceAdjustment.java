package com.red_escuela.backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnTransformer;

import java.math.BigDecimal;
import java.time.LocalDateTime;

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

    // ColumnTransformer (no JdbcTypeCode): con @JdbcTypeCode(SqlTypes.JSON), Hibernate 6 intenta
    // serializar el objeto de dominio directo con su propio JacksonJsonFormatMapper, ignorando
    // este @Convert — el cast ?::jsonb explícito es lo que hace que Postgres acepte el String ya
    // serializado por MonetaryAdjustmentValueConverter sin choque de tipos (character varying vs
    // jsonb).
    @Convert(converter = MonetaryAdjustmentValueConverter.class)
    @ColumnTransformer(write = "?::jsonb")
    @Column(name = "value", nullable = false, columnDefinition = "jsonb")
    private MonetaryAdjustmentValue value;

    // Snapshot of the base value was resolved against, at the moment it was applied: for
    // SCHOLARSHIP/DISCOUNT it's invoice.baseAmount; for SURCHARGE it's the remaining balance
    // computed BEFORE this adjustment. Stored, never recomputed later (history stays immutable).
    @Column(name = "resolved_base", nullable = false)
    private BigDecimal resolvedBase;

    @Column(name = "reason", length = 500, nullable = false)
    private String reason;

    // null = applied by the system.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "applied_by_id")
    private Person appliedBy;

    @Column(name = "applied_at", nullable = false)
    private LocalDateTime appliedAt;

    public BigDecimal signedAmount() {
        BigDecimal magnitude = value.resolve(resolvedBase);
        return type == InvoiceAdjustmentType.SURCHARGE ? magnitude : magnitude.negate();
    }
}
