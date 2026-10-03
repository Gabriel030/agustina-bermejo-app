package com.red_escuela.backend.identity.usecase.staff;

import com.red_escuela.backend.exception.PersonNotFoundException;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.entity.Role;
import com.red_escuela.backend.identity.repository.PersonRepository;
import com.red_escuela.backend.identity.service.StaffRoleAssignmentPolicy;
import com.red_escuela.backend.shared.UseCase;

import java.util.Set;

@UseCase
public class GetStaffRecordService implements GetStaffRecordUseCase {

    private final PersonRepository personRepository;
    private final StaffRoleAssignmentPolicy staffRoleAssignmentPolicy;

    public GetStaffRecordService(PersonRepository personRepository, StaffRoleAssignmentPolicy staffRoleAssignmentPolicy) {
        this.personRepository = personRepository;
        this.staffRoleAssignmentPolicy = staffRoleAssignmentPolicy;
    }

    @Override
    public Person getRecord(Long personId) {
        Person person = personRepository.findById(personId)
                .orElseThrow(() -> new PersonNotFoundException(personId));
        Set<String> visibleRoles = staffRoleAssignmentPolicy.visibleRolesForCurrentActor();
        boolean isVisibleStaff = person.getRoles().stream()
                .map(Role::getName)
                .anyMatch(visibleRoles::contains);
        if (!isVisibleStaff) {
            throw new PersonNotFoundException(personId);
        }
        return person;
    }
}
