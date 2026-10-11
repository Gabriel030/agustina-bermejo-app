package com.red_escuela.backend.billing.payment.operation;

import com.red_escuela.backend.billing.payment.service.IdempotencyRecordService;
import com.red_escuela.backend.billing.payment.service.StoredIdempotentResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.red_escuela.backend.billing.operation.BillingOperationOutcome;
import com.red_escuela.backend.billing.operation.BillingOperationType;
import com.red_escuela.backend.billing.operation.PreProcessingHandler;
import com.red_escuela.backend.billing.operation.PreProcessingResult;
import com.red_escuela.backend.billing.operation.PriorAuthorization;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.Map;

public class IdempotencyKeyHandler<C extends IdempotencyKeyed> implements PreProcessingHandler<C> {

    private static final String AUTHORIZATION_REQUEST_ID_FIELD = "authorizationRequestId";

    private final IdempotencyRecordService recordService;
    private final ObjectMapper canonicalMapper;
    private final Class<?> responseType;

    public IdempotencyKeyHandler(IdempotencyRecordService recordService, ObjectMapper objectMapper, Class<?> responseType) {
        this.recordService = recordService;
        this.canonicalMapper = objectMapper.copy()
                .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true)
                .configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true);
        this.responseType = responseType;
    }

    @Override
    public boolean mustRunFirst() {
        return true;
    }

    @Override
    public PreProcessingResult handleBeforeProcessing(BillingOperationType type, C context, PriorAuthorization priorAuthorization) {
        StoredIdempotentResponse stored = recordService.claimKeyOrFindStoredResponse(context.idempotencyKey(), type, hashOf(context), priorAuthorization);
        return switch (stored) {
            case StoredIdempotentResponse.KeyClaimed claimed -> PreProcessingResult.proceed();
            case StoredIdempotentResponse.Replayable replayable -> PreProcessingResult.replayStoredOutcome(rebuildOutcome(replayable));
        };
    }

    @Override
    public void handleAfterProcessing(BillingOperationType type, C context, Object result) {
        recordService.storeResponse(context.idempotencyKey(), IdempotencyRecordService.PROCESSED_STATUS, writeJson(result));
    }

    @Override
    public void handleAwaitingAuthorization(BillingOperationType type, C context, Long authorizationRequestId) {
        String body = writeJson(canonicalMapper.createObjectNode().put(AUTHORIZATION_REQUEST_ID_FIELD, authorizationRequestId));
        recordService.storeResponse(context.idempotencyKey(), IdempotencyRecordService.AWAITING_AUTHORIZATION_STATUS, body);
    }

    private BillingOperationOutcome<Object> rebuildOutcome(StoredIdempotentResponse.Replayable replayable) {
        if (replayable.status() == IdempotencyRecordService.AWAITING_AUTHORIZATION_STATUS) {
            return new BillingOperationOutcome.AwaitingAuthorization<>(readJson(replayable.body(), JsonNode.class).get(AUTHORIZATION_REQUEST_ID_FIELD).asLong());
        }
        return new BillingOperationOutcome.Processed<>(readJson(replayable.body(), responseType));
    }

    private String hashOf(C context) {
        try {
            ObjectNode fingerprint = canonicalMapper.createObjectNode();
            fingerprint.set("actor", canonicalMapper.valueToTree(context.actor()));
            fingerprint.set("request", withNormalizedNumbers(canonicalMapper.valueToTree(context)));
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(writeJson(fingerprint).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("The request fingerprint could not be computed", exception);
        }
    }

    private JsonNode withNormalizedNumbers(JsonNode node) {
        if (node.isNumber()) {
            return TextNode.valueOf(node.decimalValue().stripTrailingZeros().toPlainString());
        }
        if (node.isObject()) {
            ObjectNode object = (ObjectNode) node;
            Iterator<Map.Entry<String, JsonNode>> fields = object.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                field.setValue(withNormalizedNumbers(field.getValue()));
            }
        }
        if (node.isArray()) {
            ArrayNode array = (ArrayNode) node;
            for (int index = 0; index < array.size(); index++) {
                array.set(index, withNormalizedNumbers(array.get(index)));
            }
        }
        return node;
    }

    private String writeJson(Object value) {
        try {
            return canonicalMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("The idempotent payload could not be serialized", exception);
        }
    }

    private <T> T readJson(String json, Class<T> type) {
        try {
            return canonicalMapper.readValue(json, type);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("The stored idempotent response could not be read", exception);
        }
    }
}
