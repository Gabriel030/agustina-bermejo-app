package com.red_escuela.backend.billing.exception;

public class NoActiveCollectionsPolicyException extends RuntimeException {
    public NoActiveCollectionsPolicyException() {
        super("No active CollectionsPolicy — the seed migration should have inserted one");
    }
}
