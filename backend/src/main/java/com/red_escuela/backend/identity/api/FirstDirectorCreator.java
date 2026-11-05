package com.red_escuela.backend.identity.api;

import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.entity.Role;
import com.red_escuela.backend.identity.exception.DirectorAlreadyExistsException;
import com.red_escuela.backend.identity.exception.DniAlreadyExistsException;
import com.red_escuela.backend.identity.exception.EmailAlreadyExistsException;
import com.red_escuela.backend.identity.exception.RoleNotFoundException;
import com.red_escuela.backend.identity.repository.PersonRepository;
import com.red_escuela.backend.identity.repository.RoleRepository;
import com.red_escuela.backend.shared.domain.DocumentType;
import com.red_escuela.backend.shared.domain.EmailAddress;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class FirstDirectorCreator {
    private static final String DIRECTOR_ROLE = "DIRECTOR";

    private final PersonRepository personRepository;
    private final RoleRepository roleRepository;
    private final BasePersonCreator basePersonCreator;

    public FirstDirectorCreator(PersonRepository personRepository, RoleRepository roleRepository, BasePersonCreator basePersonCreator) {
        this.personRepository = personRepository;
        this.roleRepository = roleRepository;
        this.basePersonCreator = basePersonCreator;
    }

    @Transactional
    public CreatedFirstDirector createFirstDirector(String firstName, String lastName, String email, DocumentType documentType, String documentNumber) {
        if (personRepository.existsByRoles_Name(DIRECTOR_ROLE)) {
            throw new DirectorAlreadyExistsException();
        }
        if (personRepository.findByEmail(EmailAddress.of(email)).isPresent()) {
            throw new EmailAlreadyExistsException(email);
        }
        Person person = basePersonCreator.createBasePerson(firstName, lastName, email);
        person.changeDocument(documentType, documentNumber);
        person.setUsername(person.getDni());
        Role role = roleRepository.findByName(DIRECTOR_ROLE).orElseThrow(() -> new RoleNotFoundException(DIRECTOR_ROLE));
        person.getRoles().add(role);
        try {
            personRepository.saveAndFlush(person);
        } catch (DataIntegrityViolationException e) {
            throw new DniAlreadyExistsException(person.getDocumentType(), person.getDni());
        }
        return new CreatedFirstDirector(person.getId(), person.emailValue());
    }
}
