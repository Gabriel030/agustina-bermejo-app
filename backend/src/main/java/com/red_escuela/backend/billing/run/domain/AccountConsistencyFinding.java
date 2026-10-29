package com.red_escuela.backend.billing.run.domain;

import java.util.Objects;

public record AccountConsistencyFinding(Long accountId, String findingCode, String detail) {

    public AccountConsistencyFinding {
        Objects.requireNonNull(accountId, "accountId");
        Objects.requireNonNull(findingCode, "findingCode");
        Objects.requireNonNull(detail, "detail");
    }

    public String messageForDirector() {
        return "La cuenta " + accountId + " no cierra (" + findingCode + "): " + detail;
    }
}
