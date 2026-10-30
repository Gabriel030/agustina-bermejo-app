package com.red_escuela.backend.config;

import com.red_escuela.backend.shared.domain.Currency;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@ConditionalOnProperty(name = "springdoc.api-docs.enabled", havingValue = "true")
public class MoneyWireSchemaCustomizer implements OpenApiCustomizer {

    static final String MONEY_SCHEMA = "Money";
    static final String AMOUNT = "amount";
    static final String CURRENCY = "currency";
    static final String AMOUNT_PATTERN = "^-?\\d+(\\.\\d+)?$";

    @Override
    @SuppressWarnings({"rawtypes", "unchecked"})
    public void customise(OpenAPI openApi) {
        if (openApi.getComponents() == null || openApi.getComponents().getSchemas() == null) {
            return;
        }
        Schema money = openApi.getComponents().getSchemas().get(MONEY_SCHEMA);
        if (money == null) {
            return;
        }
        Schema target = money;
        if (money.getAllOf() != null) {
            target = (Schema) ((List<Schema>) money.getAllOf()).stream()
                    .filter(part -> part.getProperties() != null && part.getProperties().containsKey(AMOUNT))
                    .findFirst()
                    .orElse(money);
        }
        wireProperties().forEach(target::addProperty);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    static Map<String, Schema> wireProperties() {
        List<String> currencies = Arrays.stream(Currency.values()).map(Enum::name).toList();
        Schema currency = new StringSchema();
        currency.setEnum(currencies);
        Map<String, Schema> properties = new LinkedHashMap<>();
        properties.put(AMOUNT, new StringSchema().pattern(AMOUNT_PATTERN));
        properties.put(CURRENCY, currency);
        return properties;
    }
}
