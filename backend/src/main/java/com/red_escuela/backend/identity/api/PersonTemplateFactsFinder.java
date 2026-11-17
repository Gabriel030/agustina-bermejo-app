package com.red_escuela.backend.identity.api;

import com.red_escuela.backend.identity.entity.Institution;
import com.red_escuela.backend.identity.exception.PersonNotFoundException;
import com.red_escuela.backend.identity.repository.InstitutionRepository;
import com.red_escuela.backend.identity.repository.PersonRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class PersonTemplateFactsFinder {

    private final PersonRepository personRepository;
    private final InstitutionRepository institutionRepository;

    public PersonTemplateFactsFinder(PersonRepository personRepository, InstitutionRepository institutionRepository) {
        this.personRepository = personRepository;
        this.institutionRepository = institutionRepository;
    }

    @Transactional(readOnly = true)
    public PersonTemplateFacts findTemplateFacts(Long personId) {
        var person = personRepository.findById(personId).orElseThrow(() -> new PersonNotFoundException(personId));
        String institutionName = institutionRepository.findAll().stream().findFirst().map(Institution::getName).orElse(null);
        return new PersonTemplateFacts(person.getName(), person.getLastname(), institutionName);
    }
}
