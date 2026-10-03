package com.red_escuela.backend.billing.exception;

import com.red_escuela.backend.shared.exception.NotFoundException;

public class NoActiveCollectionsPolicyException extends NotFoundException {
    public NoActiveCollectionsPolicyException() {
        super("No active CollectionsPolicy — the seed migration should have inserted one");
    }
}
