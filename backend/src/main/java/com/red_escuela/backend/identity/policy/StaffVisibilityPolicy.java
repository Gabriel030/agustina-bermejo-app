package com.red_escuela.backend.identity.policy;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public final class StaffVisibilityPolicy {

    private static final Map<String, Set<String>> VISIBLE_ROLES_BY_ROLE = Map.of(
            "ADMINISTRATIVO", Set.of("DOCENTE", "PRECEPTOR"),
            "DIRECTOR", Set.of("DOCENTE", "PRECEPTOR", "ADMINISTRATIVO"));

    private StaffVisibilityPolicy() {
    }

    public static Set<String> visibleRoles(Collection<String> actorRoles) {
        return actorRoles.stream()
                .flatMap(actorRole -> VISIBLE_ROLES_BY_ROLE.getOrDefault(actorRole, Set.of()).stream())
                .collect(Collectors.toSet());
    }

    public static boolean canView(Collection<String> actorRoles, Collection<String> targetRoles) {
        Set<String> visible = visibleRoles(actorRoles);
        return targetRoles.stream().anyMatch(visible::contains);
    }
}
