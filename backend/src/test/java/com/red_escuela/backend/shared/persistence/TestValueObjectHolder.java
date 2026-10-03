package com.red_escuela.backend.shared.persistence;

import com.red_escuela.backend.shared.domain.DateRange;
import com.red_escuela.backend.shared.domain.EmailAddress;
import com.red_escuela.backend.shared.domain.MonetaryAdjustment;
import com.red_escuela.backend.shared.domain.Money;
import com.red_escuela.backend.shared.domain.Percentage;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnTransformer;

@Entity
@Table(name = "test_value_object_holder")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TestValueObjectHolder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "amount", column = @Column(name = "price_amount")),
            @AttributeOverride(name = "currency", column = @Column(name = "price_currency"))
    })
    private Money price;

    @Convert(converter = PercentageConverter.class)
    @Column(name = "discount_rate")
    private Percentage discountRate;

    @Convert(converter = EmailAddressConverter.class)
    @Column(name = "contact_email")
    private EmailAddress contactEmail;

    @Convert(converter = MonetaryAdjustmentConverter.class)
    @ColumnTransformer(write = "?::jsonb")
    @Column(name = "adjustment", columnDefinition = "jsonb")
    private MonetaryAdjustment adjustment;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "start", column = @Column(name = "period_start")),
            @AttributeOverride(name = "end", column = @Column(name = "period_end"))
    })
    private DateRange period;
}
