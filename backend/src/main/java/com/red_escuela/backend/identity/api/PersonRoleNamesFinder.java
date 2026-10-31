package com.red_escuela.backend.identity.api;

import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.repository.PersonRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class PersonRoleNamesFinder {

    private final PersonRepository personRepository;

    public PersonRoleNamesFinder(PersonRepository personRepository) {
        this.personRepository = personRepository;
    }

    @Transactional(readOnly = true)
    public Map<Long, List<String>> findRoleNamesByPersonId(Collection<Long> personIds) {
        if (personIds.isEmpty()) {
            return Map.of();
        }
        return personRepository.findAllWithRolesByIdIn(personIds).stream()
                .collect(Collectors.toMap(Person::getId, person -> person.getRoles().stream().map(role -> role.getName()).sorted().toList(), (first, second) -> first));
    }
}
