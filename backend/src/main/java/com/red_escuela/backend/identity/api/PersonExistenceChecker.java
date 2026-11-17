package com.red_escuela.backend.identity.api;

import com.red_escuela.backend.identity.repository.PersonRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class PersonExistenceChecker {

    private final PersonRepository personRepository;

    public PersonExistenceChecker(PersonRepository personRepository) {
        this.personRepository = personRepository;
    }

    @Transactional(readOnly = true)
    public boolean personExists(Long personId) {
        return personRepository.existsById(personId);
    }
}
