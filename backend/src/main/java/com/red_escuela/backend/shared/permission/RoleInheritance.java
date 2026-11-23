package com.red_escuela.backend.shared.permission;

import java.util.Map;
import java.util.Set;

public final class RoleInheritance {

    private static final Map<String, Set<String>> INHERITED_ROLES = Map.of(
            "SUBDIRECTOR", Set.of("ADMINISTRATIVO"));

    private RoleInheritance() {
    }

    public static Set<String> inheritedBy(String role) {
        return INHERITED_ROLES.getOrDefault(role, Set.of());
    }
}
