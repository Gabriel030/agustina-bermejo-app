package com.red_escuela.backend.billing.payment.operation;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.function.Function;

public record StoredResultMapping(Function<Object, Object> toStorable, Function<JsonNode, Object> fromStored) {
}
