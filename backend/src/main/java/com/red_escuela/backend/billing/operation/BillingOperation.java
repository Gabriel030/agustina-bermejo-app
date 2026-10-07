package com.red_escuela.backend.billing.operation;

import com.red_escuela.backend.shared.event.DomainEvent;

import java.util.List;

public interface BillingOperation<C, R> {
    BillingOperationType type();

    Class<C> contextType();

    List<String> validationMoments();

    List<Class<? extends DomainEvent>> publishedEventTypes();

    void requireValidContext(C context);

    R processOperation(C context);

    List<? extends DomainEvent> domainEventsOf(C context, R result);
}
