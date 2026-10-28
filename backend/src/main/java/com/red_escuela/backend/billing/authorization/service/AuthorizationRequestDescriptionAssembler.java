package com.red_escuela.backend.billing.authorization.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.red_escuela.backend.billing.authorization.domain.AuthorizationOperationCatalog;
import com.red_escuela.backend.billing.authorization.domain.AuthorizationRequest;
import com.red_escuela.backend.billing.operation.AuthorizationRequestDescriber;
import com.red_escuela.backend.billing.operation.AuthorizationRequestDescription;
import com.red_escuela.backend.billing.operation.AuthorizationRequestDescription.DescriptionLine;
import com.red_escuela.backend.billing.operation.AuthorizationRequestDescription.DetailBlock;
import com.red_escuela.backend.billing.operation.BillingOperation;
import com.red_escuela.backend.billing.operation.BillingOperationType;
import com.red_escuela.backend.billing.operation.BillingOperations;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Component
public class AuthorizationRequestDescriptionAssembler {
    private static final Logger log = LoggerFactory.getLogger(AuthorizationRequestDescriptionAssembler.class);

    private final Map<BillingOperationType, AuthorizationRequestDescriber<Object>> describersByType = new EnumMap<>(BillingOperationType.class);
    private final BillingOperations operations;
    private final ObjectMapper objectMapper;

    @SuppressWarnings("unchecked")
    public AuthorizationRequestDescriptionAssembler(List<AuthorizationRequestDescriber<?>> describers, BillingOperations operations, ObjectMapper objectMapper) {
        for (AuthorizationRequestDescriber<?> describer : describers) {
            if (describersByType.putIfAbsent(describer.type(), (AuthorizationRequestDescriber<Object>) describer) != null) {
                throw new IllegalStateException("Two authorization request describers declared for " + describer.type());
            }
        }
        this.operations = operations;
        this.objectMapper = objectMapper;
    }

    public AuthorizationRequestDescription describe(AuthorizationRequest request) {
        AuthorizationRequestDescription description = describeOperation(request);
        return request.isPending() ? description : new AuthorizationRequestDescription(description.summary(), description.studentName(), description.amount(),
                description.requestLines(), List.of(), description.detailBlock());
    }

    private AuthorizationRequestDescription describeOperation(AuthorizationRequest request) {
        AuthorizationRequestDescriber<Object> describer = describersByType.get(request.getOperationType());
        if (describer == null) {
            return genericDescriptionOf(request);
        }
        BillingOperation<Object, Object> operation = operations.operationOf(request.getOperationType());
        try {
            return describer.describe(objectMapper.readValue(request.getOperationPayload(), operation.contextType()), request.getId());
        } catch (JsonProcessingException | RuntimeException e) {
            log.warn("Authorization request cannot be described, falling back to the generic description: id={}, type={}", request.getId(), request.getOperationType(), e);
            return genericDescriptionOf(request);
        }
    }

    private AuthorizationRequestDescription genericDescriptionOf(AuthorizationRequest request) {
        String label = AuthorizationOperationCatalog.labelOf(request.getOperationType());
        return new AuthorizationRequestDescription(label, null, null, List.of(new DescriptionLine("Operación", label), new DescriptionLine("Motivo", request.getReason())), List.of(),
                DetailBlock.none());
    }
}
