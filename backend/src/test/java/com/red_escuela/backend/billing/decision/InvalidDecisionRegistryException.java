package com.red_escuela.backend.billing.decision;

import java.util.List;

public class InvalidDecisionRegistryException extends RuntimeException {

    private final List<String> errors;

    public InvalidDecisionRegistryException(List<String> errors) {
        super(String.join(System.lineSeparator(), errors));
        this.errors = List.copyOf(errors);
    }

    public List<String> errors() {
        return errors;
    }
}
