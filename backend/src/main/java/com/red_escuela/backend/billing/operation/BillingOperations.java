package com.red_escuela.backend.billing.operation;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public class BillingOperations {
    private final Map<BillingOperationType, BillingOperation<?, ?>> operationsByType = new EnumMap<>(BillingOperationType.class);

    public BillingOperations(List<BillingOperation<?, ?>> operations) {
        for (BillingOperation<?, ?> operation : operations) {
            if (operationsByType.putIfAbsent(operation.type(), operation) != null) {
                throw new IllegalStateException("Two operations declared for " + operation.type());
            }
        }
    }

    @SuppressWarnings("unchecked")
    public <C, R> BillingOperation<C, R> operationOf(BillingOperationType type) {
        BillingOperation<?, ?> operation = operationsByType.get(type);
        if (operation == null) {
            throw new IllegalStateException("No operation registered for " + type);
        }
        return (BillingOperation<C, R>) operation;
    }

    public List<BillingOperation<?, ?>> listAll() {
        return List.copyOf(operationsByType.values());
    }
}
