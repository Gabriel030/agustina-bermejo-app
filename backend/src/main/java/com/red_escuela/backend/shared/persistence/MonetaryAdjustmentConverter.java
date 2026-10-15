package com.red_escuela.backend.shared.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.shared.domain.MonetaryAdjustment;
import com.red_escuela.backend.shared.domain.Money;
import com.red_escuela.backend.shared.domain.Percentage;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.math.BigDecimal;

@Converter
public class MonetaryAdjustmentConverter implements AttributeConverter<MonetaryAdjustment, String> {

    private static final String TYPE = "type";
    private static final String NOMINAL = "NOMINAL";
    private static final String PROPORTIONAL = "PROPORTIONAL";
    private static final String AMOUNT = "amount";
    private static final String CURRENCY = "currency";
    private static final String FRACTION = "fraction";

    private final ObjectMapper objectMapper;

    public MonetaryAdjustmentConverter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public String convertToDatabaseColumn(MonetaryAdjustment adjustment) {
        if (adjustment == null) {
            return null;
        }
        ObjectNode node = objectMapper.createObjectNode();
        switch (adjustment) {
            case MonetaryAdjustment.Nominal nominal -> {
                node.put(TYPE, NOMINAL);
                node.put(AMOUNT, nominal.amount().amount().toPlainString());
                node.put(CURRENCY, nominal.amount().currency().name());
            }
            case MonetaryAdjustment.Proportional proportional -> {
                node.put(TYPE, PROPORTIONAL);
                node.put(FRACTION, proportional.rate().fraction().toPlainString());
            }
        }
        return node.toString();
    }

    @Override
    public MonetaryAdjustment convertToEntityAttribute(String json) {
        if (json == null) {
            return null;
        }
        JsonNode node = readTree(json);
        String type = requiredText(node, TYPE, json);
        return switch (type) {
            case NOMINAL -> new MonetaryAdjustment.Nominal(Money.of(
                    new BigDecimal(requiredText(node, AMOUNT, json)),
                    Currency.valueOf(requiredText(node, CURRENCY, json))));
            case PROPORTIONAL -> new MonetaryAdjustment.Proportional(
                    Percentage.ofFraction(new BigDecimal(requiredText(node, FRACTION, json))));
            default -> throw new IllegalStateException("Unknown monetary adjustment type: " + type);
        };
    }

    private JsonNode readTree(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not parse monetary adjustment: " + json, exception);
        }
    }

    private static String requiredText(JsonNode node, String field, String json) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            throw new IllegalStateException("Missing field " + field + " in monetary adjustment: " + json);
        }
        return value.asText();
    }
}
