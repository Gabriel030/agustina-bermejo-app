package com.red_escuela.backend.identity.api;

import com.red_escuela.backend.identity.entity.Institution;
import com.red_escuela.backend.identity.repository.InstitutionRepository;
import com.red_escuela.backend.identity.repository.PersonRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class PersonTemplateFactsFinder {

    private static final int LOOKUP_CHUNK_SIZE = 1000;

    private final PersonRepository personRepository;
    private final InstitutionRepository institutionRepository;

    public PersonTemplateFactsFinder(PersonRepository personRepository, InstitutionRepository institutionRepository) {
        this.personRepository = personRepository;
        this.institutionRepository = institutionRepository;
    }

    @Transactional(readOnly = true)
    public Optional<PersonTemplateFacts> findTemplateFacts(Long personId) {
        String institutionName = institutionRepository.findAll().stream().findFirst().map(Institution::getName).orElse(null);
        return personRepository.findById(personId)
                .map(person -> new PersonTemplateFacts(person.getName(), person.getLastname(), institutionName));
    }

    @Transactional(readOnly = true)
    public Map<Long, PersonTemplateFacts> findTemplateFactsAll(Collection<Long> personIds) {
        Map<Long, PersonTemplateFacts> facts = new HashMap<>();
        if (personIds.isEmpty()) {
            return facts;
        }
        String institutionName = institutionRepository.findAll().stream().findFirst().map(Institution::getName).orElse(null);
        List<Long> distinctIds = personIds.stream().distinct().toList();
        for (int from = 0; from < distinctIds.size(); from += LOOKUP_CHUNK_SIZE) {
            List<Long> chunk = distinctIds.subList(from, Math.min(from + LOOKUP_CHUNK_SIZE, distinctIds.size()));
            personRepository.findAllById(chunk).forEach(person ->
                    facts.put(person.getId(), new PersonTemplateFacts(person.getName(), person.getLastname(), institutionName)));
        }
        return facts;
    }
}
