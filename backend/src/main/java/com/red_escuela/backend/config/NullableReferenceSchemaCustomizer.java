package com.red_escuela.backend.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.Schema;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
@ConditionalOnProperty(name = "springdoc.api-docs.enabled", havingValue = "true")
public class NullableReferenceSchemaCustomizer implements OpenApiCustomizer {

    private static final String NULL_TYPE = "null";

    @Override
    @SuppressWarnings({"rawtypes"})
    public void customise(OpenAPI openApi) {
        if (openApi.getComponents() == null || openApi.getComponents().getSchemas() == null) {
            return;
        }
        Map<String, Schema> schemas = openApi.getComponents().getSchemas();
        schemas.values().forEach(NullableReferenceSchemaCustomizer::repairNestedSchemas);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void repairNestedSchemas(Schema<?> schema) {
        if (schema == null) {
            return;
        }
        Map<String, Schema> properties = schema.getProperties();
        if (properties != null) {
            properties.replaceAll((name, property) -> repairReference(property));
            properties.values().forEach(NullableReferenceSchemaCustomizer::repairNestedSchemas);
        }
        repairNestedSchemas(schema.getItems());
        if (schema.getAdditionalProperties() instanceof Schema additional) {
            repairNestedSchemas(additional);
        }
        repairAll(schema.getAllOf());
        repairAll(schema.getOneOf());
        repairAll(schema.getAnyOf());
    }

    @SuppressWarnings({"rawtypes"})
    private static void repairAll(Collection<Schema> schemas) {
        if (schemas != null) {
            schemas.forEach(NullableReferenceSchemaCustomizer::repairNestedSchemas);
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Schema repairReference(Schema property) {
        if (property.get$ref() == null || !declaresNullType(property)) {
            return property;
        }
        List<Schema> alternatives = new ArrayList<>();
        alternatives.add(new Schema().$ref(property.get$ref()));
        Schema nullSchema = new Schema();
        nullSchema.setTypes(Set.of(NULL_TYPE));
        alternatives.add(nullSchema);
        Schema repaired = new Schema();
        repaired.setOneOf(alternatives);
        repaired.setDescription(property.getDescription());
        return repaired;
    }

    private static boolean declaresNullType(Schema<?> property) {
        Set<String> types = property.getTypes();
        return NULL_TYPE.equals(property.getType()) || (types != null && types.contains(NULL_TYPE));
    }
}
