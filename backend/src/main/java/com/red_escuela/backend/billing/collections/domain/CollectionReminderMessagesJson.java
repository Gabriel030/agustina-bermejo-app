package com.red_escuela.backend.billing.collections.domain;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.util.List;

final class CollectionReminderMessagesJson {

    private static final ObjectMapper MAPPER = new ObjectMapper().findAndRegisterModules()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private CollectionReminderMessagesJson() {
    }

    static String write(List<?> values) {
        try {
            return MAPPER.writeValueAsString(values);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("The collection reminder values cannot be serialized", e);
        }
    }

    static <T> List<T> read(String json, TypeReference<List<T>> type) {
        try {
            return MAPPER.readValue(json, type);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("The stored collection reminder values cannot be read", e);
        }
    }
}
