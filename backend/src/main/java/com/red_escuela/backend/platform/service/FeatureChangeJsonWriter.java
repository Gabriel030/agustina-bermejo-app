package com.red_escuela.backend.platform.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.red_escuela.backend.platform.domain.FeatureState;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class FeatureChangeJsonWriter {

    private final ObjectMapper objectMapper;

    public FeatureChangeJsonWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String writeState(FeatureState state) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("enabled", state.enabled());
        fields.put("mode", state.mode());
        fields.put("version", state.version());
        try {
            return objectMapper.writeValueAsString(fields);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("No se pudo serializar el estado de la feature", e);
        }
    }
}
