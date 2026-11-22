package com.red_escuela.backend.config;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class RoleAuthorityExpander {

    private static final Map<String, Set<String>> INHERITED_ROLES = Map.of(
            "SUBDIRECTOR", Set.of("ADMINISTRATIVO"));

    private RoleAuthorityExpander() {
    }

    public static Set<String> expand(List<String> roles) {
        Set<String> expanded = new LinkedHashSet<>(roles);
        roles.forEach(role -> expanded.addAll(INHERITED_ROLES.getOrDefault(role, Set.of())));
        return expanded;
    }
}
