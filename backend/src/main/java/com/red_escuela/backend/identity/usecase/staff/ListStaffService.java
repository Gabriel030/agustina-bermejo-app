package com.red_escuela.backend.identity.usecase.staff;

import com.red_escuela.backend.entity.StatusType;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.entity.Role;
import com.red_escuela.backend.identity.repository.PersonRepository;
import com.red_escuela.backend.identity.service.CurrentPersonService;
import com.red_escuela.backend.identity.service.StaffRoleAssignmentPolicy;
import com.red_escuela.backend.repository.PersonSpecifications;
import com.red_escuela.backend.shared.UseCase;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@UseCase
public class ListStaffService implements ListStaffUseCase {

    private static final Map<String, Set<String>> VISIBLE_ROLES_BY_ACTOR = Map.of(
            "ADMINISTRATIVO", Set.of("DOCENTE", "PRECEPTOR"),
            "DIRECTOR", Set.of("DOCENTE", "PRECEPTOR", "ADMINISTRATIVO")
    );

    private final PersonRepository personRepository;
    private final CurrentPersonService currentPersonService;

    public ListStaffService(PersonRepository personRepository, CurrentPersonService currentPersonService) {
        this.personRepository = personRepository;
        this.currentPersonService = currentPersonService;
    }

    @Override
    public List<Person> list(ListStaffCommand command) {
        Person actor = currentPersonService.resolveCurrentPerson();
        Set<String> actorRoles = actor.getRoles().stream().map(Role::getName).collect(Collectors.toSet());

        Set<String> visibleRoles = actorRoles.stream()
                .flatMap(r -> VISIBLE_ROLES_BY_ACTOR.getOrDefault(r, Set.of()).stream())
                .collect(Collectors.toSet());

        if (command.roleFilter() != null && !command.roleFilter().isBlank()) {
            visibleRoles = visibleRoles.contains(command.roleFilter())
                    ? Set.of(command.roleFilter())
                    : Set.of();
        }

        StatusType effectiveStatus = command.status() != null ? command.status() : StatusType.ACTIVE;

        Specification<Person> spec = Specification
                .where(PersonSpecifications.withRoleIn(visibleRoles))
                .and(PersonSpecifications.withStatus(effectiveStatus))
                .and(PersonSpecifications.withSearch(command.search()));

        return personRepository.findAll(spec, Sort.by("lastname", "name"));
    }
}
