package com.red_escuela.backend.identity.api;

import com.red_escuela.backend.identity.entity.AuthProvider;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.repository.AddressRepository;
import com.red_escuela.backend.identity.repository.PersonRepository;
import com.red_escuela.backend.shared.domain.Address;
import com.red_escuela.backend.shared.domain.EmailAddress;
import com.red_escuela.backend.shared.domain.StatusType;
import com.red_escuela.backend.shared.persistence.InClauseChunks;
import java.util.Collection;
import java.util.List;
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

    @Transactional(readOnly = true)
    public boolean isEmailRegistered(String email) {
        return personRepository.findByEmail(EmailAddress.of(email)).isPresent();
    }

    @Transactional
    public void deleteBasePerson(Person person) {
        Address address = person.getAddress();
        personRepository.delete(person);
        personRepository.flush();
        if (address != null) {
            addressRepository.delete(address);
            addressRepository.flush();
        }
    }

    @Transactional
    public void deleteBasePersons(Collection<Long> personIds) {
        for (List<Long> chunk : InClauseChunks.of(personIds)) {
            List<Long> addressIds = personRepository.findAddressIdsByIdIn(chunk);
            personRepository.deleteRolesByPersonIdIn(chunk);
            personRepository.deleteByIdIn(chunk);
            for (List<Long> addressChunk : InClauseChunks.of(addressIds)) {
                addressRepository.deleteByIdIn(addressChunk);
            }
        }
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
