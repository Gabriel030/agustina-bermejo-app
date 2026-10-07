package com.red_escuela.backend.billing.operation;

import java.util.Set;

public enum AuthorizationRole {
    DIRECTOR(Set.of("DIRECTOR")),
    DIRECTOR_OR_ADMINISTRATIVE(Set.of("DIRECTOR", "ADMINISTRATIVO"));

    private final Set<String> allowedRoleNames;

    AuthorizationRole(Set<String> allowedRoleNames) {
        this.allowedRoleNames = allowedRoleNames;
    }

    public boolean isSatisfiedByAnyOf(Set<String> roleNames) {
        return roleNames.stream().anyMatch(allowedRoleNames::contains);
    }

    public Set<String> allowedRoleNames() {
        return allowedRoleNames;
    }
}
