package com.red_escuela.backend.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.core.util.Json31;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class NullableReferenceSchemaCustomizerTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Path SNAPSHOT = Path.of("..", "contracts", "openapi.json");
    private static final String TARGET_REF = "#/components/schemas/Target";

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static OpenAPI openApiWith(Schema property) {
        Schema owner = new ObjectSchema();
        owner.addProperty("field", property);
        return new OpenAPI().components(new Components().addSchemas("Owner", owner));
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Schema nullableReference() {
        Schema reference = new Schema().$ref(TARGET_REF);
        reference.setTypes(Set.of("null"));
        return reference;
    }

    private static JsonNode repairedField(OpenAPI openApi) {
        new NullableReferenceSchemaCustomizer().customise(openApi);
        return Json31.mapper().valueToTree(openApi.getComponents().getSchemas().get("Owner")).get("properties").get("field");
    }

    @Test
    void nullableReferenceBecomesOneOfReferenceOrNull() {
        JsonNode field = repairedField(openApiWith(nullableReference()));

        assertThat(field.has("$ref")).isFalse();
        assertThat(field.has("type")).isFalse();
        assertThat(field.get("oneOf")).hasSize(2);
        assertThat(field.get("oneOf").get(0).get("$ref").asText()).isEqualTo(TARGET_REF);
        assertThat(field.get("oneOf").get(1).get("type").asText()).isEqualTo("null");
    }

    @Test
    void plainReferenceIsLeftUntouched() {
        JsonNode field = repairedField(openApiWith(new Schema().$ref(TARGET_REF)));

        assertThat(field.get("$ref").asText()).isEqualTo(TARGET_REF);
        assertThat(field.has("oneOf")).isFalse();
    }

    @Test
    void nullableReferenceNestedInsideArrayItemsIsRepaired() {
        Schema<?> items = new ObjectSchema();
        items.addProperty("inner", nullableReference());
        Schema<?> array = new Schema<>();
        array.setTypes(Set.of("array"));
        array.setItems(items);
        JsonNode field = repairedField(openApiWith(array));

        assertThat(field.get("items").get("properties").get("inner").has("oneOf")).isTrue();
    }

    @Test
    void committedContractHasNoReferenceNextToNullType() throws Exception {
        List<String> offenders = new ArrayList<>();
        collectOffenders(MAPPER.readTree(Files.readString(SNAPSHOT)), "", offenders);

        assertThat(offenders).isEmpty();
    }

    private static void collectOffenders(JsonNode node, String path, List<String> offenders) {
        if (node.isObject()) {
            JsonNode type = node.get("type");
            if (node.has("$ref") && type != null && ("null".equals(type.asText()) || (type.isArray() && containsNull(type)))) {
                offenders.add(path);
            }
            node.fields().forEachRemaining(entry -> collectOffenders(entry.getValue(), path + "/" + entry.getKey(), offenders));
        } else if (node.isArray()) {
            for (int i = 0; i < node.size(); i++) {
                collectOffenders(node.get(i), path + "/" + i, offenders);
            }
        }
    }

    private static boolean containsNull(JsonNode types) {
        for (JsonNode type : types) {
            if ("null".equals(type.asText())) {
                return true;
            }
        }
        return false;
    }
}
