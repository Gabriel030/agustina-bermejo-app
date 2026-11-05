package com.red_escuela.backend.platform.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.red_escuela.backend.platform.domain.FeatureStateView;
import com.red_escuela.backend.shared.module.BillingMode;
import org.springframework.stereotype.Service;

@Service
public class FeatureChangeJsonReader {

    private final ObjectMapper objectMapper;

    public FeatureChangeJsonReader(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public FeatureStateView readStateView(String json) {
        if (json == null) {
            return null;
        }
        try {
            JsonNode node = objectMapper.readTree(json);
            JsonNode mode = node.get("mode");
            BillingMode billingMode = mode == null || mode.isNull() ? null : BillingMode.valueOf(mode.asText());
            return new FeatureStateView(node.get("enabled").asBoolean(), billingMode);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("No se pudo leer el estado registrado de la feature", e);
        }
    }
}
