package com.red_escuela.backend.identity.service;
import com.red_escuela.backend.identity.repository.PersonRepository;

import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.exception.PersonNotFoundException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
public class CurrentPersonService {
    private final PersonRepository personRepository;

    public CurrentPersonService(PersonRepository personRepository) {
        this.personRepository = personRepository;
    }

    public Person resolveCurrentPerson() {
        Long personId = Long.valueOf(SecurityContextHolder.getContext().getAuthentication().getName());
        return personRepository.findById(personId)
                .orElseThrow(() -> new PersonNotFoundException(personId));
    }
}
