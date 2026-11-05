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
import com.red_escuela.backend.billing.payment.domain.CashPaymentLimits;
import com.red_escuela.backend.billing.payment.exception.IdempotencyKeyInvalidException;
import com.red_escuela.backend.shared.domain.policy.PolicyViolation;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class IdempotencyKeyHandler<C extends IdempotencyKeyed> implements PreProcessingHandler<C> {

    private static final String AUTHORIZATION_REQUEST_ID_FIELD = "authorizationRequestId";

    private final IdempotencyRecordService recordService;
    private final ObjectMapper canonicalMapper;
    private final StoredResultMapping resultMapping;
    private final boolean optionalKeyBeforeValidation;

    public IdempotencyKeyHandler(IdempotencyRecordService recordService, ObjectMapper objectMapper, Class<?> responseType) {
        this(recordService, objectMapper, responseType, null);
    }

    public IdempotencyKeyHandler(IdempotencyRecordService recordService, ObjectMapper objectMapper, StoredResultMapping resultMapping) {
        this(recordService, objectMapper, null, resultMapping);
    }

    private IdempotencyKeyHandler(IdempotencyRecordService recordService, ObjectMapper objectMapper, Class<?> responseType, StoredResultMapping resultMapping) {
        this.recordService = recordService;
        this.canonicalMapper = objectMapper.copy()
                .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true)
                .configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true);
        this.optionalKeyBeforeValidation = resultMapping != null;
        this.resultMapping = resultMapping != null ? resultMapping : new StoredResultMapping(result -> result, body -> readJson(body, responseType));
    }

    @Override
    public boolean mustRunFirst() {
        return true;
    }

    @Override
    public boolean runsBeforeContextValidation() {
        return optionalKeyBeforeValidation;
    }

    @Override
    public PreProcessingResult handleBeforeProcessing(BillingOperationType type, C context, PriorAuthorization priorAuthorization) {
        if (isSkipped(context)) {
            return PreProcessingResult.proceed();
        }
        StoredIdempotentResponse stored = recordService.claimKeyOrFindStoredResponse(context.idempotencyKey(), type, hashOf(context), priorAuthorization);
        return switch (stored) {
            case StoredIdempotentResponse.KeyClaimed claimed -> PreProcessingResult.proceed();
            case StoredIdempotentResponse.Replayable replayable -> PreProcessingResult.replayStoredOutcome(rebuildOutcome(replayable));
        };
    }

    @Override
    public void handleAfterProcessing(BillingOperationType type, C context, Object result) {
        if (isSkipped(context)) {
            return;
        }
        recordService.storeResponse(context.idempotencyKey(), IdempotencyRecordService.PROCESSED_STATUS, writeJson(resultMapping.toStorable().apply(result)));
    }

    @Override
    public void handleAwaitingAuthorization(BillingOperationType type, C context, Long authorizationRequestId) {
        if (isSkipped(context)) {
            return;
        }
        String body = writeJson(canonicalMapper.createObjectNode().put(AUTHORIZATION_REQUEST_ID_FIELD, authorizationRequestId));
        recordService.storeResponse(context.idempotencyKey(), IdempotencyRecordService.AWAITING_AUTHORIZATION_STATUS, body);
    }

    private BillingOperationOutcome<Object> rebuildOutcome(StoredIdempotentResponse.Replayable replayable) {
        if (replayable.status() == IdempotencyRecordService.AWAITING_AUTHORIZATION_STATUS) {
            return new BillingOperationOutcome.AwaitingAuthorization<>(readJson(replayable.body(), JsonNode.class).get(AUTHORIZATION_REQUEST_ID_FIELD).asLong());
        }
        return new BillingOperationOutcome.Processed<>(resultMapping.fromStored().apply(replayable.body()));
    }

    private boolean isSkipped(C context) {
        if (!optionalKeyBeforeValidation) {
            return false;
        }
        String key = context.idempotencyKey();
        if (key == null) {
            return true;
        }
        if (key.isBlank() || key.length() > CashPaymentLimits.MAX_IDEMPOTENCY_KEY_LENGTH) {
            throw new IdempotencyKeyInvalidException(List.of(new PolicyViolation("IDEMPOTENCY_KEY_INVALID", "La clave de idempotencia tiene hasta 100 caracteres y no puede estar vacía.")));
        }
        return false;
    }

    private String hashOf(C context) {
        try {
            ObjectNode fingerprint = canonicalMapper.createObjectNode();
            fingerprint.set("actor", canonicalMapper.valueToTree(context.actor()));
            fingerprint.set("request", withNormalizedNumbers(canonicalMapper.valueToTree(context.requestFingerprint())));
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
