package com.red_escuela.backend.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.Schema;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
@ConditionalOnProperty(name = "springdoc.api-docs.enabled", havingValue = "true")
public class NullableEnumSchemaCustomizer implements OpenApiCustomizer {

    private static final String NULL_TYPE = "null";

    @Override
    @SuppressWarnings({"rawtypes"})
    public void customise(OpenAPI openApi) {
        if (openApi.getComponents() == null || openApi.getComponents().getSchemas() == null) {
            return;
        }
        Map<String, Schema> schemas = openApi.getComponents().getSchemas();
        schemas.values().forEach(NullableEnumSchemaCustomizer::admitNullInEnums);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void admitNullInEnums(Schema<?> schema) {
        if (schema == null) {
            return;
        }
        Map<String, Schema> properties = schema.getProperties();
        if (properties != null) {
            properties.values().forEach(property -> {
                if (declaresNullType(property) && property.getEnum() != null && !property.getEnum().contains(null)) {
                    List<Object> values = new ArrayList<>(property.getEnum());
                    values.add(null);
                    property.setEnum(values);
                }
                admitNullInEnums(property);
            });
        }
        admitNullInEnums(schema.getItems());
    }

    private static boolean declaresNullType(Schema<?> property) {
        Set<String> types = property.getTypes();
        return NULL_TYPE.equals(property.getType()) || (types != null && types.contains(NULL_TYPE));
    }
}
