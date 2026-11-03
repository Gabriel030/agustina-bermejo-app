package com.red_escuela.backend.identity.api;

import com.red_escuela.backend.identity.repository.InstitutionRepository;
import com.red_escuela.backend.identity.repository.PersonRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class FirstDirectorStatusFinder {
    private static final String DIRECTOR_ROLE = "DIRECTOR";

    private final PersonRepository personRepository;
    private final InstitutionRepository institutionRepository;

    public FirstDirectorStatusFinder(PersonRepository personRepository, InstitutionRepository institutionRepository) {
        this.personRepository = personRepository;
        this.institutionRepository = institutionRepository;
    }

    @Transactional(readOnly = true)
    public FirstDirectorStatus findFirstDirectorStatus() {
        String institutionName = institutionRepository.findAll().stream()
                .findFirst()
                .map(institution -> institution.getName())
                .orElse(null);
        return new FirstDirectorStatus(personRepository.existsByRoles_Name(DIRECTOR_ROLE), institutionName);
    }
}
