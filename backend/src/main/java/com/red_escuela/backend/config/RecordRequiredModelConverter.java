package com.red_escuela.backend.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.type.TypeFactory;
import io.swagger.v3.core.converter.AnnotatedType;
import io.swagger.v3.core.converter.ModelConverter;
import io.swagger.v3.core.converter.ModelConverterContext;
import io.swagger.v3.oas.models.media.Schema;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
@ConditionalOnProperty(name = "springdoc.api-docs.enabled", havingValue = "true")
public class RecordRequiredModelConverter implements ModelConverter {

    private static final String REF_PREFIX = "#/components/schemas/";

    @Override
    @SuppressWarnings("rawtypes")
    public Schema resolve(AnnotatedType type, ModelConverterContext context, Iterator<ModelConverter> chain) {
        if (!chain.hasNext()) {
            return null;
        }
        Schema resolved = chain.next().resolve(type, context, chain);
        if (resolved == null || type.getType() == null) {
            return resolved;
        }
        Class<?> rawClass = TypeFactory.defaultInstance().constructType(type.getType()).getRawClass();
        if (!rawClass.isRecord()) {
            return resolved;
        }
        Schema target = resolveTarget(resolved, context);
        if (target != null && target.getProperties() != null) {
            applyRequired(target, rawClass);
        }
        return resolved;
    }

    @SuppressWarnings("rawtypes")
    private Schema resolveTarget(Schema resolved, ModelConverterContext context) {
        String ref = resolved.get$ref();
        if (ref == null) {
            return resolved;
        }
        return context.getDefinedModels().get(ref.substring(REF_PREFIX.length()));
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void applyRequired(Schema schema, Class<?> recordClass) {
        Map<String, Schema> properties = schema.getProperties();
        List<String> required = new ArrayList<>();
        for (RecordComponent component : recordClass.getRecordComponents()) {
            String propertyName = propertyName(component);
            if (!properties.containsKey(propertyName)) {
                continue;
            }
            if (isNullable(component)) {
                properties.get(propertyName).setNullable(true);
            } else {
                required.add(propertyName);
            }
        }
        schema.setRequired(required.isEmpty() ? null : required);
    }

    private String propertyName(RecordComponent component) {
        JsonProperty annotation = component.getAnnotation(JsonProperty.class);
        if (annotation != null && !annotation.value().isEmpty()) {
            return annotation.value();
        }
        return component.getName();
    }

    private boolean isNullable(RecordComponent component) {
        return Optional.class.equals(component.getType())
                || hasNullableAnnotation(component.getAnnotations())
                || hasNullableAnnotation(component.getAccessor().getAnnotations())
                || hasNullableAnnotation(declaredField(component).getAnnotations());
    }

    private Field declaredField(RecordComponent component) {
        try {
            return component.getDeclaringRecord().getDeclaredField(component.getName());
        } catch (NoSuchFieldException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private boolean hasNullableAnnotation(Annotation[] annotations) {
        return Arrays.stream(annotations)
                .anyMatch(annotation -> annotation.annotationType().getSimpleName().equals("Nullable"));
    }
}
