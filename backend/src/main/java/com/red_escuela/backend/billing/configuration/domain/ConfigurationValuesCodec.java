package com.red_escuela.backend.billing.configuration.domain;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.json.JsonMapper;

public final class ConfigurationValuesCodec {
    private static final ObjectMapper MAPPER = JsonMapper.builder()
            .findAndAddModules()
            .disable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS)
            .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
            .build();

    private ConfigurationValuesCodec() {
    }

    public static String toJson(ConfigurationValues values) {
        try {
            return MAPPER.writeValueAsString(values);
        } catch (JacksonException e) {
            throw new IllegalStateException("Configuration values cannot be serialized", e);
        }
    }

    public static ConfigurationValues fromJson(String json) {
        try {
            return MAPPER.readValue(json, ConfigurationValues.class);
        } catch (JacksonException e) {
            throw new IllegalStateException("Configuration values cannot be read", e);
        }
    }
}
