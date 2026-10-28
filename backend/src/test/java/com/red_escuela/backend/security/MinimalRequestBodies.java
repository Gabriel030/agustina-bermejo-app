package com.red_escuela.backend.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

final class MinimalRequestBodies {

    private static final Path SNAPSHOT = Path.of("..", "contracts", "openapi.json");
    private static final int MAX_DEPTH = 6;
    private static final JsonNodeFactory NODES = JsonNodeFactory.instance;

    private final JsonNode specification;

    MinimalRequestBodies() {
        try {
            this.specification = new ObjectMapper().readTree(Files.readString(SNAPSHOT));
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo leer " + SNAPSHOT, e);
        }
    }

    Optional<String> jsonBodyFor(String method, String path) {
        return contentSchema(method, path, "application/json").map(schema -> sample(schema, 0).toString());
    }

    Map<String, String> multipartTextFieldsFor(String method, String path) {
        Map<String, String> fields = new LinkedHashMap<>();
        multipartProperties(method, path).forEach((name, schema) -> {
            if (!isBinary(resolve(schema))) {
                fields.put(name, sample(schema, 0).asText());
            }
        });
        return fields;
    }

    Optional<String> multipartFilePartFor(String method, String path) {
        return multipartProperties(method, path).entrySet().stream()
                .filter(entry -> isBinary(resolve(entry.getValue())))
                .map(Map.Entry::getKey)
                .findFirst();
    }

    boolean isMultipart(String method, String path) {
        return contentSchema(method, path, "multipart/form-data").isPresent();
    }

    private Map<String, JsonNode> multipartProperties(String method, String path) {
        Map<String, JsonNode> properties = new LinkedHashMap<>();
        contentSchema(method, path, "multipart/form-data")
                .map(this::resolve)
                .ifPresent(schema -> schema.path("properties").properties()
                        .forEach(entry -> properties.put(entry.getKey(), entry.getValue())));
        return properties;
    }

    private boolean isBinary(JsonNode schema) {
        if ("binary".equals(schema.path("format").asText())) {
            return true;
        }
        return "array".equals(schema.path("type").asText()) && isBinary(resolve(schema.path("items")));
    }

    private Optional<JsonNode> contentSchema(String method, String path, String mediaType) {
        JsonNode content = specification.path("paths").path(path).path(method.toLowerCase(Locale.ROOT))
                .path("requestBody").path("content").path(mediaType);
        return content.isMissingNode() ? Optional.empty() : Optional.of(content.path("schema"));
    }

    private JsonNode resolve(JsonNode schema) {
        JsonNode reference = schema.path("$ref");
        if (reference.isMissingNode()) {
            return schema;
        }
        String name = reference.asText().substring(reference.asText().lastIndexOf('/') + 1);
        return specification.path("components").path("schemas").path(name);
    }

    private JsonNode sample(JsonNode rawSchema, int depth) {
        JsonNode schema = resolve(rawSchema);
        if (depth > MAX_DEPTH) {
            return NODES.nullNode();
        }
        String reference = rawSchema.path("$ref").asText("");
        if (reference.endsWith("/Money")) {
            return NODES.objectNode().put("amount", 1).put("currency", "ARS");
        }
        if (reference.endsWith("/Percentage")) {
            return NODES.numberNode(10);
        }
        if (schema.has("example")) {
            return schema.get("example");
        }
        if (schema.has("enum")) {
            return schema.get("enum").get(0);
        }
        if (schema.has("allOf")) {
            return sample(schema.get("allOf").get(0), depth + 1);
        }
        if (schema.has("oneOf")) {
            return sample(schema.get("oneOf").get(0), depth + 1);
        }
        if (schema.has("anyOf")) {
            return sample(schema.get("anyOf").get(0), depth + 1);
        }
        return switch (typeOf(schema)) {
            case "object" -> sampleObject(schema, depth);
            case "array" -> sampleArray(schema, depth);
            case "integer" -> NODES.numberNode(sampleWhole(schema));
            case "number" -> NODES.numberNode(sampleWhole(schema));
            case "boolean" -> NODES.booleanNode(true);
            default -> NODES.textNode(sampleText(schema));
        };
    }

    private String typeOf(JsonNode schema) {
        JsonNode type = schema.path("type");
        if (type.isArray()) {
            for (JsonNode candidate : type) {
                if (!"null".equals(candidate.asText())) {
                    return candidate.asText();
                }
            }
        }
        if (type.isMissingNode() && schema.has("properties")) {
            return "object";
        }
        return type.asText("string");
    }

    private ObjectNode sampleObject(JsonNode schema, int depth) {
        ObjectNode object = NODES.objectNode();
        JsonNode required = schema.path("required");
        JsonNode properties = schema.path("properties");
        for (JsonNode requiredName : required) {
            JsonNode property = properties.path(requiredName.asText());
            if (!property.isMissingNode()) {
                object.set(requiredName.asText(), formattedTextByName(requiredName.asText(), property)
                        .<JsonNode>map(NODES::textNode)
                        .orElseGet(() -> sample(property, depth + 1)));
            }
        }
        return object;
    }

    private Optional<String> formattedTextByName(String name, JsonNode property) {
        JsonNode schema = resolve(property);
        boolean plainText = "string".equals(schema.path("type").asText()) && !schema.has("format") && !schema.has("enum");
        if (!plainText) {
            return Optional.empty();
        }
        if (name.endsWith("Time") || name.endsWith("WindowStart") || name.endsWith("WindowEnd")) {
            return Optional.of("09:00");
        }
        if (name.endsWith("Period")) {
            return Optional.of("2026-01");
        }
        return Optional.empty();
    }

    private ArrayNode sampleArray(JsonNode schema, int depth) {
        ArrayNode array = NODES.arrayNode();
        if (schema.path("minItems").asInt(0) > 0) {
            array.add(sample(schema.path("items"), depth + 1));
        }
        return array;
    }

    private long sampleWhole(JsonNode schema) {
        long value = 1;
        if (schema.has("minimum")) {
            value = schema.get("minimum").asLong();
            if (schema.path("exclusiveMinimum").asBoolean(false)) {
                value++;
            }
        }
        if (schema.has("exclusiveMinimum") && schema.get("exclusiveMinimum").isNumber()) {
            value = schema.get("exclusiveMinimum").asLong() + 1;
        }
        if (schema.has("maximum")) {
            value = Math.min(value, schema.get("maximum").asLong());
        }
        return value;
    }

    private String sampleText(JsonNode schema) {
        if ("^\\d{4}-\\d{2}$".equals(schema.path("pattern").asText())) {
            return "2026-01";
        }
        return switch (schema.path("format").asText("")) {
            case "date" -> "2026-01-15";
            case "date-time" -> "2099-01-15T10:00:00Z";
            case "email" -> "matrix@example.com";
            case "uuid" -> "00000000-0000-0000-0000-000000000001";
            default -> "x".repeat(Math.max(1, schema.path("minLength").asInt(1)));
        };
    }
}
