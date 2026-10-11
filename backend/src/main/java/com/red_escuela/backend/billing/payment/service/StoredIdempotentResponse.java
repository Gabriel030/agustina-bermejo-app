package com.red_escuela.backend.billing.payment.service;

public sealed interface StoredIdempotentResponse {

    enum KeyClaimed implements StoredIdempotentResponse {
        INSTANCE
    }

    record Replayable(int status, String body) implements StoredIdempotentResponse {
    }
}
