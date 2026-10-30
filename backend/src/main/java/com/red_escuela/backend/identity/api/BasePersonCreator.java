package com.red_escuela.backend.identity.api;

import com.red_escuela.backend.identity.entity.AuthProvider;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.repository.AddressRepository;
import com.red_escuela.backend.identity.repository.PersonRepository;
import com.red_escuela.backend.shared.domain.Address;
import com.red_escuela.backend.shared.domain.StatusType;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class BasePersonCreator {
    private final PersonRepository personRepository;
    private final AddressRepository addressRepository;

    public BasePersonCreator(PersonRepository personRepository, AddressRepository addressRepository) {
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
}
