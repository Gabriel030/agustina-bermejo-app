package com.red_escuela.backend.identity.service;

import com.red_escuela.backend.identity.api.PersonNameLookup;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.repository.PersonRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class RepositoryPersonNameLookup implements PersonNameLookup {

    private final PersonRepository personRepository;

    public RepositoryPersonNameLookup(PersonRepository personRepository) {
        this.personRepository = personRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<Long, String> findFullNamesByPersonId(Collection<Long> personIds) {
        if (personIds.isEmpty()) {
            return Map.of();
        }
        return personRepository.findAllById(personIds).stream()
                .collect(Collectors.toMap(Person::getId, person -> person.getName() + " " + person.getLastname(), (first, second) -> first));
    }
}
