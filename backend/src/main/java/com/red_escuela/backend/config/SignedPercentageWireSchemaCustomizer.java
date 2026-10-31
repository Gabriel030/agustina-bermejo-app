package com.red_escuela.backend.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.NumberSchema;
import io.swagger.v3.oas.models.media.Schema;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@ConditionalOnProperty(name = "springdoc.api-docs.enabled", havingValue = "true")
public class SignedPercentageWireSchemaCustomizer implements OpenApiCustomizer {

    static final String SIGNED_PERCENTAGE_SCHEMA = "SignedPercentage";
    private static final String SIGNED_PERCENTAGE_REF = "#/components/schemas/" + SIGNED_PERCENTAGE_SCHEMA;

    @Override
    @SuppressWarnings({"rawtypes", "unchecked"})
    public void customise(OpenAPI openApi) {
        if (openApi.getComponents() == null || openApi.getComponents().getSchemas() == null) {
            return;
        }
        Map<String, Schema> schemas = openApi.getComponents().getSchemas();
        schemas.values().forEach(schema -> replaceSignedPercentageProperties(schema));
        schemas.remove(SIGNED_PERCENTAGE_SCHEMA);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void replaceSignedPercentageProperties(Schema<?> schema) {
        Map<String, Schema> properties = schema.getProperties();
        if (properties == null) {
            return;
        }
        properties.replaceAll((name, property) -> SIGNED_PERCENTAGE_REF.equals(property.get$ref()) ? new NumberSchema() : property);
    }
}
