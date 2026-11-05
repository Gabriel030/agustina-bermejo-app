package com.red_escuela.backend.identity.service;

import com.red_escuela.backend.identity.api.BasePersonCreator;
import com.red_escuela.backend.identity.entity.AuthProvider;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.repository.PersonRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PersonFactory {
    private final PersonRepository personRepository;
    private final BasePersonCreator basePersonCreator;

    public PersonFactory(PersonRepository personRepository, BasePersonCreator basePersonCreator) {
        this.personRepository = personRepository;
        this.basePersonCreator = basePersonCreator;
    }

    @Transactional
    public Person createBasePerson(String firstName, String lastName, String email) {
        return basePersonCreator.createBasePerson(firstName, lastName, email);
    }

    @Transactional
    public Person createPersonWithCredentials(
            String firstName, String lastName, String email, String username, String passwordHasheada) {
        Person person = createBasePerson(firstName, lastName, email);
        person.setUsername(username);
        person.setPassword(passwordHasheada);
        return personRepository.save(person);
    }

    @Transactional
    public Person createPersonWithGoogle(String firstName, String lastName, String email, String googleSubjectId) {
        Person person = createBasePerson(firstName, lastName, email);
        person.setUsername(email);
        person.setAuthProvider(AuthProvider.GOOGLE);
        person.setGoogleSubjectId(googleSubjectId);
        return personRepository.save(person);
    }
}
