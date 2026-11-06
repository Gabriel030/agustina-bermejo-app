package com.red_escuela.backend.billing.payment.operation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.function.Function;

public record StoredResultMapping(Function<Object, Object> toStorable, Function<String, Object> fromStored) {

    public static long storedId(ObjectMapper objectMapper, String body, String field) {
        try {
            return objectMapper.readTree(body).get(field).asLong();
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("The stored idempotent response could not be read", exception);
        }
    }
}
