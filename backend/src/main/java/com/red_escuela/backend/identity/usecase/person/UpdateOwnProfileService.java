package com.red_escuela.backend.identity.usecase.person;

import com.red_escuela.backend.dto.UpdateOwnProfileRequest;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.exception.DniAlreadyExistsException;
import com.red_escuela.backend.identity.repository.PersonRepository;
import com.red_escuela.backend.identity.service.CurrentPersonService;
import com.red_escuela.backend.shared.UseCase;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

@UseCase
public class UpdateOwnProfileService implements UpdateOwnProfileUseCase {

    private final PersonRepository personRepository;
    private final CurrentPersonService currentPersonService;

    public UpdateOwnProfileService(PersonRepository personRepository, CurrentPersonService currentPersonService) {
        this.personRepository = personRepository;
        this.currentPersonService = currentPersonService;
    }

    @Override
    @Transactional
    public Person updateOwnProfile(UpdateOwnProfileRequest request) {
        Person person = currentPersonService.resolveCurrentPerson();

        if (request.email() != null) {
            person.setEmail(request.email());
        }
        if (request.telefono() != null) {
            person.setPhoneNumber(request.telefono());
        }
        if (request.dni() != null) {
            person.setDni(request.dni());
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

        try {
            return personRepository.saveAndFlush(person);
        } catch (DataIntegrityViolationException e) {
            throw new DniAlreadyExistsException(request.dni());
        }
    }
}
