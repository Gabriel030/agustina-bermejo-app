package com.red_escuela.backend.identity.usecase.staff;

import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.entity.Role;
import com.red_escuela.backend.identity.service.CurrentPersonService;
import com.red_escuela.backend.identity.service.StaffRoleAssignmentPolicy;
import com.red_escuela.backend.shared.domain.Address;
import com.red_escuela.backend.shared.domain.StatusType;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

final class StaffTestFixtures {

    private StaffTestFixtures() {
    }

    static Person personWithRoles(Long id, StatusType status, String... roleNames) {
        Person person = new Person();
        person.setId(id);
        person.setStatus(status);
        person.setEmail("person" + id + "@example.com");
        person.setAddress(new Address());
        for (String roleName : roleNames) {
            Role role = new Role();
            role.setName(roleName);
            person.getRoles().add(role);
        }
        return person;
    }

    static StaffRoleAssignmentPolicy policyActingAs(String actorRole) {
        CurrentPersonService currentPersonService = mock(CurrentPersonService.class);
        when(currentPersonService.resolveCurrentPerson())
                .thenReturn(personWithRoles(1L, StatusType.ACTIVE, actorRole));
        return new StaffRoleAssignmentPolicy(currentPersonService);
    }
}
