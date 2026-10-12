package com.red_escuela.backend.billing.operation;

import java.util.function.LongFunction;

public sealed interface BillingOperationOutcome<R> {

    R resultOrThrowIfAwaitingAuthorization(LongFunction<? extends RuntimeException> exceptionFactory);

    record Processed<R>(R result) implements BillingOperationOutcome<R> {
        @Override
        public R resultOrThrowIfAwaitingAuthorization(LongFunction<? extends RuntimeException> exceptionFactory) {
            return result;
        }
    }

    record AwaitingAuthorization<R>(Long authorizationRequestId) implements BillingOperationOutcome<R> {
        @Override
        public R resultOrThrowIfAwaitingAuthorization(LongFunction<? extends RuntimeException> exceptionFactory) {
            throw exceptionFactory.apply(authorizationRequestId);
        }
    }
}
