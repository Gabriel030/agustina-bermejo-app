package com.red_escuela.backend.billing.collections.domain;

import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

final class CollectionReminderMessagesJson {

    private static final ObjectMapper MAPPER = JsonMapper.builder()
            .findAndAddModules()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .disable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS)
            .build();

    private CollectionReminderMessagesJson() {
    }

    static String write(List<?> values) {
        try {
            return MAPPER.writeValueAsString(values);
        } catch (JacksonException e) {
            throw new IllegalStateException("The collection reminder values cannot be serialized", e);
        }
    }

    static <T> List<T> read(String json, TypeReference<List<T>> type) {
        try {
            return MAPPER.readValue(json, type);
        } catch (JacksonException e) {
            throw new IllegalStateException("The stored collection reminder values cannot be read", e);
        }
    }
}
