package com.red_escuela.backend.billing.authorization.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.red_escuela.backend.billing.authorization.domain.AuthorizationRequest;
import com.red_escuela.backend.billing.authorization.domain.AuthorizationResumeOutcome;
import com.red_escuela.backend.billing.operation.BillingOperation;
import com.red_escuela.backend.billing.operation.BillingOperationPipeline;
import com.red_escuela.backend.billing.operation.BillingOperations;
import org.springframework.stereotype.Component;

@Component
public class AuthorizedOperationResumer {
    private final BillingOperations operations;
    private final BillingOperationPipeline pipeline;
    private final ObjectMapper objectMapper;

    public AuthorizedOperationResumer(BillingOperations operations, BillingOperationPipeline pipeline, ObjectMapper objectMapper) {
        this.operations = operations;
        this.pipeline = pipeline;
        this.objectMapper = objectMapper;
    }

    public void resumeApprovedOperation(AuthorizationRequest request) {
        BillingOperation<Object, Object> operation = operations.operationOf(request.getOperationType());
        pipeline.resumeAuthorizedOperation(operation, deserializeContext(operation, request.getOperationPayload()));
        request.recordResumeOutcome(AuthorizationResumeOutcome.PROCESSED);
    }

    private Object deserializeContext(BillingOperation<Object, Object> operation, String payload) {
        try {
            return objectMapper.readValue(payload, operation.contextType());
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Stored operation context cannot be read for " + operation.type(), e);
        }
    }
}
