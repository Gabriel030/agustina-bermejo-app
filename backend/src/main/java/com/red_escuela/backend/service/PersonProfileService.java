package com.red_escuela.backend.service;

import com.red_escuela.backend.dto.UpdateOwnProfileRequest;
import com.red_escuela.backend.entity.Person;
import com.red_escuela.backend.exception.DniAlreadyExistsException;
import com.red_escuela.backend.exception.PersonNotFoundException;
import com.red_escuela.backend.repository.PersonRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * RF-AL-13: cualquier rol autenticado edita sus propios datos. El Person nunca se
 * resuelve desde un id de la URL, sino del "sub" del JWT (JwtAuthenticationFilter
 * lo deja como el name del Authentication) — así el endpoint no puede, ni por error
 * de implementación, afectar a otra persona.
 */
@Service
public class PersonProfileService {

    private final PersonRepository personRepository;

    public PersonProfileService(PersonRepository personRepository) {
        this.personRepository = personRepository;
    }

    public Person verPerfilPropio() {
        return resolveAuthenticatedPerson();
    }

    @Transactional
    public Person updateOwnData(UpdateOwnProfileRequest request) {
        Person person = resolveAuthenticatedPerson();

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

    private Person resolveAuthenticatedPerson() {
        Long personId = Long.valueOf(SecurityContextHolder.getContext().getAuthentication().getName());
        return personRepository.findById(personId)
                .orElseThrow(() -> new PersonNotFoundException(personId));
    }
}
