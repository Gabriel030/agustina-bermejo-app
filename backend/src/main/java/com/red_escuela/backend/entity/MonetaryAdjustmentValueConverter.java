package com.red_escuela.backend.entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.math.BigDecimal;

@Converter
public class MonetaryAdjustmentValueConverter implements AttributeConverter<MonetaryAdjustmentValue, String> {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public String convertToDatabaseColumn(MonetaryAdjustmentValue value) {
        if (value == null) {
            return null;
        }
        ObjectNode node = MAPPER.createObjectNode();
        if (value instanceof MonetaryAdjustmentValue.FixedAmount fixed) {
            node.put("type", "FIXED");
            node.put("amount", fixed.amount());
        } else if (value instanceof MonetaryAdjustmentValue.PercentageAmount percentage) {
            node.put("type", "PERCENTAGE");
            node.put("percentage", percentage.percentage());
        }
        return node.toString();
    }

    @Override
    public MonetaryAdjustmentValue convertToEntityAttribute(String dbData) {
        if (dbData == null) {
            return null;
        }
        try {
            JsonNode node = MAPPER.readTree(dbData);
            String type = node.get("type").asText();
            return switch (type) {
                case "FIXED" -> new MonetaryAdjustmentValue.FixedAmount(new BigDecimal(node.get("amount").asText()));
                case "PERCENTAGE" -> new MonetaryAdjustmentValue.PercentageAmount(new BigDecimal(node.get("percentage").asText()));
                default -> throw new IllegalStateException("Unknown MonetaryAdjustmentValue type: " + type);
            };
        } catch (Exception e) {
            throw new IllegalStateException("Could not parse MonetaryAdjustmentValue from: " + dbData, e);
        }
    }
}
