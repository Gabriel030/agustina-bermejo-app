package com.red_escuela.backend.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.NumberSchema;
import io.swagger.v3.oas.models.media.Schema;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Component
@ConditionalOnProperty(name = "springdoc.api-docs.enabled", havingValue = "true")
public class PercentageWireSchemaCustomizer implements OpenApiCustomizer {

    static final String PERCENTAGE_SCHEMA = "Percentage";
    static final String PERCENTAGE_DESCRIPTION = "Porcentaje en puntos, de 0 a 100 (por ejemplo 12.5 es 12,5 %)";
    private static final String PERCENTAGE_REF = "#/components/schemas/" + PERCENTAGE_SCHEMA;

    @Override
    @SuppressWarnings({"rawtypes"})
    public void customise(OpenAPI openApi) {
        if (openApi.getComponents() == null || openApi.getComponents().getSchemas() == null) {
            return;
        }
        Map<String, Schema> schemas = openApi.getComponents().getSchemas();
        schemas.values().forEach(PercentageWireSchemaCustomizer::replaceReferences);
        schemas.remove(PERCENTAGE_SCHEMA);
    }

    static Schema<?> wireSchema() {
        NumberSchema schema = new NumberSchema();
        schema.setMinimum(BigDecimal.ZERO);
        schema.setMaximum(new BigDecimal("100"));
        schema.setDescription(PERCENTAGE_DESCRIPTION);
        return schema;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void replaceReferences(Schema<?> schema) {
        if (schema == null) {
            return;
        }
        Map<String, Schema> properties = schema.getProperties();
        if (properties != null) {
            properties.replaceAll((name, property) -> replacement(property));
            properties.values().forEach(PercentageWireSchemaCustomizer::replaceReferences);
        }
        if (isPercentageReference(schema.getItems())) {
            schema.setItems(wireSchema());
        }
        replaceReferences(schema.getItems());
        if (schema.getAdditionalProperties() instanceof Schema additional) {
            if (isPercentageReference(additional)) {
                schema.setAdditionalProperties(wireSchema());
            } else {
                replaceReferences(additional);
            }
        }
        replaceInList(schema.getAllOf());
        replaceInList(schema.getOneOf());
        replaceInList(schema.getAnyOf());
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void replaceInList(List<Schema> parts) {
        if (parts == null) {
            return;
        }
        parts.replaceAll(PercentageWireSchemaCustomizer::replacement);
        parts.forEach(PercentageWireSchemaCustomizer::replaceReferences);
    }

    @SuppressWarnings({"rawtypes"})
    private static Schema replacement(Schema property) {
        return isPercentageReference(property) ? wireSchema() : property;
    }

    private static boolean isPercentageReference(Schema<?> schema) {
        return schema != null && PERCENTAGE_REF.equals(schema.get$ref());
    }
}
