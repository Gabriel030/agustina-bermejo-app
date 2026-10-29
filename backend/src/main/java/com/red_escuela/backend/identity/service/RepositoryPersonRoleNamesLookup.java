package com.red_escuela.backend.identity.service;

import com.red_escuela.backend.identity.api.PersonRoleNamesLookup;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.repository.PersonRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class RepositoryPersonRoleNamesLookup implements PersonRoleNamesLookup {

    private final PersonRepository personRepository;

    public RepositoryPersonRoleNamesLookup(PersonRepository personRepository) {
        this.personRepository = personRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<Long, List<String>> findRoleNamesByPersonId(Collection<Long> personIds) {
        if (personIds.isEmpty()) {
            return Map.of();
        }
        return personRepository.findAllWithRolesByIdIn(personIds).stream()
                .collect(Collectors.toMap(Person::getId, person -> person.getRoles().stream().map(role -> role.getName()).sorted().toList(), (first, second) -> first));
    }
}
