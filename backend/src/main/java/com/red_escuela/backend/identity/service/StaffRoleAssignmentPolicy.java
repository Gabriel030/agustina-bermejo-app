package com.red_escuela.backend.identity.service;

import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.entity.Role;
import com.red_escuela.backend.identity.exception.RoleNotAssignableByActorException;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class StaffRoleAssignmentPolicy {
    public static final Set<String> STAFF_ROLES = Set.of("DOCENTE", "PRECEPTOR", "ADMINISTRATIVO");

    private static final Map<String, Set<String>> CREATABLE_ROLES_BY_ROLE = Map.of(
            "ADMINISTRATIVO", Set.of("DOCENTE", "PRECEPTOR"),
            "DIRECTOR", Set.of("ADMINISTRATIVO"));

    private static final Map<String, Set<String>> VISIBLE_ROLES_BY_ROLE = Map.of(
            "ADMINISTRATIVO", Set.of("DOCENTE", "PRECEPTOR"),
            "DIRECTOR", Set.of("DOCENTE", "PRECEPTOR", "ADMINISTRATIVO"));

    private final CurrentPersonService currentPersonService;

    public StaffRoleAssignmentPolicy(CurrentPersonService currentPersonService) {
        this.currentPersonService = currentPersonService;
    }

    public void requireCanAssign(String targetRole) {
        if (targetRole == null) {
            throw new RoleNotAssignableByActorException("desconocido");
        }
        Person actor = currentPersonService.resolveCurrentPerson();
        boolean autorizado = actor.getRoles().stream()
                .map(Role::getName)
                .anyMatch(actorRole -> CREATABLE_ROLES_BY_ROLE.getOrDefault(actorRole, Set.of()).contains(targetRole));
        if (!autorizado) {
            throw new RoleNotAssignableByActorException(targetRole);
        }
    }

    public Set<String> visibleRolesForCurrentActor() {
        Person actor = currentPersonService.resolveCurrentPerson();
        return actor.getRoles().stream()
                .map(Role::getName)
                .flatMap(actorRole -> VISIBLE_ROLES_BY_ROLE.getOrDefault(actorRole, Set.of()).stream())
                .collect(Collectors.toSet());
    }

    public String resolveStaffRole(Person person) {
        return person.getRoles().stream()
                .map(Role::getName)
                .filter(STAFF_ROLES::contains)
                .findFirst()
                .orElse(null);
    }
}
