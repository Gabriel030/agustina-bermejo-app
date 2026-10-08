package com.red_escuela.backend.billing.configuration.domain;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;

public final class ConfigurationValuesCodec {
    private static final ObjectMapper MAPPER = JsonMapper.builder()
            .findAndAddModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
            .build();

    private ConfigurationValuesCodec() {
    }

    public static String toJson(ConfigurationValues values) {
        try {
            return MAPPER.writeValueAsString(values);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Configuration values cannot be serialized", e);
        }
    }

    public static ConfigurationValues fromJson(String json) {
        try {
            return MAPPER.readValue(json, ConfigurationValues.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Configuration values cannot be read", e);
        }
    }
}
