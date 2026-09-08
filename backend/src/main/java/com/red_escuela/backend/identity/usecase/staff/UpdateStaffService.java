package com.red_escuela.backend.identity.usecase.staff;

import com.red_escuela.backend.identity.dto.UpdateStaffRequest;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.exception.PersonNotFoundException;
import com.red_escuela.backend.identity.repository.PersonRepository;
import com.red_escuela.backend.identity.service.StaffRoleAssignmentPolicy;
import com.red_escuela.backend.shared.UseCase;
import org.springframework.transaction.annotation.Transactional;

@UseCase
public class UpdateStaffService implements UpdateStaffUseCase {

    private final PersonRepository personRepository;
    private final StaffRoleAssignmentPolicy staffRoleAssignmentPolicy;

    public UpdateStaffService(PersonRepository personRepository, StaffRoleAssignmentPolicy staffRoleAssignmentPolicy) {
        this.personRepository = personRepository;
        this.staffRoleAssignmentPolicy = staffRoleAssignmentPolicy;
    }

    @Override
    @Transactional
    public StaffUseCaseResult update(UpdateStaffCommand command) {
        Person person = personRepository.findById(command.personId())
                .orElseThrow(() -> new PersonNotFoundException(command.personId()));
        String currentRole = staffRoleAssignmentPolicy.resolveStaffRole(person);
        staffRoleAssignmentPolicy.requireCanAssign(currentRole);

        UpdateStaffRequest request = command.request();
        if (request.email() != null) {
            person.setEmail(request.email());
        }
        if (request.telefono() != null) {
            person.setPhoneNumber(request.telefono());
        }

        var address = person.getAddress();
        if (request.street() != null) {
            address.setStreet(request.street());
        }
        if (request.number() != null) {
            address.setNumber(request.number());
        }
        if (request.floor() != null) {
            address.setFloor(request.floor());
        }
        if (request.city() != null) {
            address.setCity(request.city());
        }
        if (request.state() != null) {
            address.setState(request.state());
        }
        if (request.postalCode() != null) {
            address.setPostalCode(request.postalCode());
        }
        if (request.country() != null) {
            address.setCountry(request.country());
        }

        Person saved = personRepository.save(person);
        return new StaffUseCaseResult(saved, currentRole);
    }
}
