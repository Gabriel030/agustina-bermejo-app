package com.red_escuela.backend.billing.operation;

import java.util.List;
import java.util.Objects;

public record OperationPreHandlers(BillingOperationType type, List<PreProcessingHandler<?>> ordered) {
    public OperationPreHandlers {
        Objects.requireNonNull(type, "type");
        ordered = List.copyOf(ordered);
    }
}
