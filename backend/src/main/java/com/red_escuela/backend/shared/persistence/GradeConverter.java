package com.red_escuela.backend.shared.persistence;

import com.red_escuela.backend.shared.domain.Grade;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.math.BigDecimal;

@Converter(autoApply = true)
public class GradeConverter implements AttributeConverter<Grade, BigDecimal> {

    @Override
    public BigDecimal convertToDatabaseColumn(Grade grade) {
        return grade == null ? null : grade.value();
    }

    @Override
    public Grade convertToEntityAttribute(BigDecimal value) {
        return value == null ? null : Grade.of(value);
    }
}
