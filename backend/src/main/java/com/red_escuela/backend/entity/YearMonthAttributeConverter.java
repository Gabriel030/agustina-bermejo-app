package com.red_escuela.backend.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.sql.Date;
import java.time.YearMonth;

// Hibernate has no built-in mapping for java.time.YearMonth — stored as the first day of that
// month (a plain DATE column), autoApply so every YearMonth field in the module gets it for free.
@Converter(autoApply = true)
public class YearMonthAttributeConverter implements AttributeConverter<YearMonth, Date> {

    @Override
    public Date convertToDatabaseColumn(YearMonth attribute) {
        return attribute == null ? null : Date.valueOf(attribute.atDay(1));
    }

    @Override
    public YearMonth convertToEntityAttribute(Date dbData) {
        return dbData == null ? null : YearMonth.from(dbData.toLocalDate());
    }
}
