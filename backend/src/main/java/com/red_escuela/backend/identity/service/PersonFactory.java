package com.red_escuela.backend.identity.service;

import com.red_escuela.backend.entity.Address;
import com.red_escuela.backend.identity.entity.AuthProvider;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.entity.StatusType;
import com.red_escuela.backend.repository.AddressRepository;
import com.red_escuela.backend.identity.repository.PersonRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PersonFactory {
    private final PersonRepository personRepository;
    private final AddressRepository addressRepository;

    public PersonFactory(PersonRepository personRepository, AddressRepository addressRepository) {
        this.personRepository = personRepository;
        this.addressRepository = addressRepository;
    }

    @Transactional
    public Person createBasePerson(String firstName, String lastName, String email) {
        Address address = new Address();
        addressRepository.save(address);

        Person person = new Person();
        person.setName(firstName);
        person.setLastname(lastName);
        person.setEmail(email);
        person.setAddress(address);
        person.setAuthProvider(AuthProvider.LOCAL);
        person.setPassword(null);
        person.setUsername(null);
        person.setStatus(StatusType.ACTIVE);

        return personRepository.save(person);
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
