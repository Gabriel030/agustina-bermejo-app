package com.red_escuela.backend.billing.operation;

import com.red_escuela.backend.shared.domain.statemachine.StatusChangeActor;

public interface IdempotencyKeyed {
    String idempotencyKey();

    StatusChangeActor actor();

    default Object requestFingerprint() {
        return this;
    }
}
