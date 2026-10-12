package com.red_escuela.backend.billing.operation;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class AuthorizationRequestObservers implements AuthorizationRequestObserver {
    private final List<AuthorizationRequestObserver> observers;

    public AuthorizationRequestObservers(ObjectProvider<AuthorizationRequestObserver> observers) {
        this.observers = observers.orderedStream().filter(observer -> !(observer instanceof AuthorizationRequestObservers)).toList();
    }

    @Override
    public void afterOpened(Long requestId, BillingOperationType type, Object context) {
        observers.forEach(observer -> observer.afterOpened(requestId, type, context));
    }

    @Override
    public void afterRejected(Long requestId, BillingOperationType type, Long deciderId, String reason) {
        observers.forEach(observer -> observer.afterRejected(requestId, type, deciderId, reason));
    }

    @Override
    public void afterWithdrawn(Long requestId, BillingOperationType type, Long requesterId) {
        observers.forEach(observer -> observer.afterWithdrawn(requestId, type, requesterId));
    }

    @Override
    public void afterExpired(Long requestId, BillingOperationType type) {
        observers.forEach(observer -> observer.afterExpired(requestId, type));
    }

    @Override
    public void afterResumeRejected(Long requestId, BillingOperationType type, Long deciderId, String reason) {
        observers.forEach(observer -> observer.afterResumeRejected(requestId, type, deciderId, reason));
    }
}
