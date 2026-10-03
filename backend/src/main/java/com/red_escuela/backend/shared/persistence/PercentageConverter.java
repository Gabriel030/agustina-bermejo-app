package com.red_escuela.backend.shared.persistence;

import com.red_escuela.backend.shared.domain.Percentage;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.math.BigDecimal;

@Converter
public class PercentageConverter implements AttributeConverter<Percentage, BigDecimal> {

    @Override
    public BigDecimal convertToDatabaseColumn(Percentage percentage) {
        return percentage == null ? null : percentage.fraction();
    }

    @Override
    public Percentage convertToEntityAttribute(BigDecimal fraction) {
        return fraction == null ? null : Percentage.ofFraction(fraction);
    }
}
