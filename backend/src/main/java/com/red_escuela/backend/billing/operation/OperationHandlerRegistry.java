package com.red_escuela.backend.billing.operation;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public class OperationHandlerRegistry {
    private final Map<BillingOperationType, List<PreProcessingHandler<?>>> handlersByType = new EnumMap<>(BillingOperationType.class);

    public OperationHandlerRegistry(List<OperationPreHandlers> registrations) {
        for (OperationPreHandlers registration : registrations) {
            if (handlersByType.putIfAbsent(registration.type(), registration.ordered()) != null) {
                throw new IllegalStateException("Pre-processing handlers declared twice for " + registration.type());
            }
        }
    }

    @SuppressWarnings("unchecked")
    public <C> List<PreProcessingHandler<C>> preHandlersFor(BillingOperationType type) {
        List<PreProcessingHandler<C>> handlers = new ArrayList<>();
        for (PreProcessingHandler<?> handler : handlersByType.getOrDefault(type, List.of())) {
            handlers.add((PreProcessingHandler<C>) handler);
        }
        return handlers;
    }

    public List<PreProcessingHandler<?>> listPreHandlersFor(BillingOperationType type) {
        return handlersByType.getOrDefault(type, List.of());
    }
}
