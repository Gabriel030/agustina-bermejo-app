package com.red_escuela.backend.identity.usecase.staff;

import com.red_escuela.backend.entity.StatusType;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.repository.PersonRepository;
import com.red_escuela.backend.identity.service.StaffRoleAssignmentPolicy;
import com.red_escuela.backend.repository.PersonSpecifications;
import com.red_escuela.backend.shared.UseCase;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;
import java.util.Set;

@UseCase
public class ListStaffService implements ListStaffUseCase {

    private final PersonRepository personRepository;
    private final StaffRoleAssignmentPolicy staffRoleAssignmentPolicy;

    public ListStaffService(PersonRepository personRepository, StaffRoleAssignmentPolicy staffRoleAssignmentPolicy) {
        this.personRepository = personRepository;
        this.staffRoleAssignmentPolicy = staffRoleAssignmentPolicy;
    }

    @Override
    public List<Person> list(ListStaffCommand command) {
        Set<String> visibleRoles = staffRoleAssignmentPolicy.visibleRolesForCurrentActor();

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
