package com.red_escuela.backend.billing.authorization.domain;

import com.red_escuela.backend.billing.operation.BillingOperationType;

import java.util.HashSet;
import java.util.Set;

public record AuthorizationRequestSearch(Set<AuthorizationRequestStatus> statuses, Set<BillingOperationType> operationTypes, Set<AuthorizationOperationFamily> families,
                                         Long requestedBy, AuthorizationRequestSort sort, boolean ascending, int page, int size) {

    public static final int MAX_PAGE_SIZE = 50;
    public static final int DEFAULT_PAGE_SIZE = 20;

    public AuthorizationRequestSearch {
        statuses = statuses == null ? Set.of() : Set.copyOf(statuses);
        operationTypes = operationTypes == null ? Set.of() : Set.copyOf(operationTypes);
        families = families == null ? Set.of() : Set.copyOf(families);
        sort = sort == null ? AuthorizationRequestSort.EXPIRES_ON : sort;
        page = Math.max(page, 0);
        size = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
    }

    public Set<BillingOperationType> matchingOperationTypes() {
        Set<BillingOperationType> byFamily = AuthorizationOperationCatalog.typesOf(families);
        if (operationTypes.isEmpty() && families.isEmpty()) {
            return Set.of();
        }
        if (families.isEmpty()) {
            return operationTypes;
        }
        if (operationTypes.isEmpty()) {
            return byFamily;
        }
        Set<BillingOperationType> both = new HashSet<>(operationTypes);
        both.retainAll(byFamily);
        return both;
    }

    public boolean filtersOperationTypes() {
        return !operationTypes.isEmpty() || !families.isEmpty();
    }
}
