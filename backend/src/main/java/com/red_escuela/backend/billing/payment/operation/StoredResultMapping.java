package com.red_escuela.backend.billing.payment.operation;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.util.function.Function;

public record StoredResultMapping(Function<Object, Object> toStorable, Function<String, Object> fromStored) {

    public static long storedId(ObjectMapper objectMapper, String body, String field) {
        try {
            return objectMapper.readTree(body).get(field).asLong();
        } catch (JacksonException exception) {
            throw new IllegalStateException("The stored idempotent response could not be read", exception);
        }
    }
}
