package com.red_escuela.backend.identity.service;

import com.red_escuela.backend.identity.exception.PersonNotFoundException;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.entity.Role;
import com.red_escuela.backend.identity.exception.RoleNotAssignableByActorException;
import com.red_escuela.backend.identity.policy.StaffVisibilityPolicy;
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

    private final CurrentPersonService currentPersonService;

    private final EmailChangeRequester emailChangeRequester;

    public StaffRoleAssignmentPolicy(CurrentPersonService currentPersonService, EmailChangeRequester emailChangeRequester) {
        this.currentPersonService = currentPersonService;
        this.emailChangeRequester = emailChangeRequester;
    }

    public void requireNoEmailChangeInFlight(Person target) {
        emailChangeRequester.requireNoEmailChangeInFlight(target);
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

    public void requireManageableOrNotFound(Long personId, String targetRole) {
        try {
            requireCanAssign(targetRole);
        } catch (RoleNotAssignableByActorException exception) {
            throw new PersonNotFoundException(personId);
        }
    }

    public Set<String> visibleRolesForCurrentActor() {
        Person actor = currentPersonService.resolveCurrentPerson();
        return StaffVisibilityPolicy.visibleRoles(actor.getRoles().stream().map(Role::getName).toList());
    }

    public String resolveStaffRole(Person person) {
        return person.getRoles().stream()
                .map(Role::getName)
                .filter(STAFF_ROLES::contains)
                .findFirst()
                .orElse(null);
    }
}
