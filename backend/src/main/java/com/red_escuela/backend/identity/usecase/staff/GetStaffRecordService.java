package com.red_escuela.backend.identity.usecase.staff;

import com.red_escuela.backend.exception.PersonNotFoundException;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.entity.Role;
import com.red_escuela.backend.identity.repository.PersonRepository;
import com.red_escuela.backend.identity.service.StaffRoleAssignmentPolicy;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class GetStaffRecordService implements GetStaffRecordUseCase {

    private final PersonRepository personRepository;

    public GetStaffRecordService(PersonRepository personRepository) {
        this.personRepository = personRepository;
    }

    @Override
    public Person getRecord(Long personId) {
        Person person = personRepository.findById(personId)
                .orElseThrow(() -> new PersonNotFoundException(personId));
        boolean isStaff = person.getRoles().stream()
                .map(Role::getName)
                .anyMatch(StaffRoleAssignmentPolicy.STAFF_ROLES::contains);
        if (!isStaff) {
            throw new PersonNotFoundException(personId);
        }
        return person;
    }
}
