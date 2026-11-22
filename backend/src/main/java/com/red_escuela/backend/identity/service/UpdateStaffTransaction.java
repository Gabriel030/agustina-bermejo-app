package com.red_escuela.backend.identity.service;

import com.red_escuela.backend.identity.dto.UpdateStaffRequest;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.exception.PersonNotFoundException;
import com.red_escuela.backend.identity.repository.PersonRepository;
import com.red_escuela.backend.identity.specialist.EmailChangeRequester;
import com.red_escuela.backend.identity.specialist.StaffRoleAssignmentPolicy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UpdateStaffTransaction {
    private final PersonRepository personRepository;
    private final StaffRoleAssignmentPolicy staffRoleAssignmentPolicy;
    private final EmailChangeRequester emailChangeRequester;

    public UpdateStaffTransaction(PersonRepository personRepository, StaffRoleAssignmentPolicy staffRoleAssignmentPolicy,
                                  EmailChangeRequester emailChangeRequester) {
        this.personRepository = personRepository;
        this.staffRoleAssignmentPolicy = staffRoleAssignmentPolicy;
        this.emailChangeRequester = emailChangeRequester;
    }

    @Transactional
    public UpdatedStaff update(Long personId, UpdateStaffRequest request) {
        Person person = personRepository.findById(personId)
                .orElseThrow(() -> new PersonNotFoundException(personId));
        String currentRole = staffRoleAssignmentPolicy.resolveStaffRole(person);
        staffRoleAssignmentPolicy.requireCanAssign(currentRole);

        if (request.email() != null) {
            emailChangeRequester.requestStaffChange(person, request.email());
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
        return new UpdatedStaff(saved, currentRole);
    }
}
