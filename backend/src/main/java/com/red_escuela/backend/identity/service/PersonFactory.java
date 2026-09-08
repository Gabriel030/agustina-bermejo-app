package com.red_escuela.backend.identity.service;

import com.red_escuela.backend.entity.Address;
import com.red_escuela.backend.identity.entity.AuthProvider;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.entity.StatusType;
import com.red_escuela.backend.repository.AddressRepository;
import com.red_escuela.backend.identity.repository.PersonRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Patrón Factory: crear una Person siempre implica crear primero su Address vacía
 * (Person.address es not-null pero no tiene cascada) — ProspectService y StaffService
 * necesitan exactamente esa misma secuencia de dos pasos, así que se encapsula acá
 * en vez de duplicarla en ambos servicios.
 */
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
        // Explícito, no queda en null: AuthService.login rechaza cualquier status != ACTIVE.
        person.setStatus(StatusType.ACTIVE);

        return personRepository.save(person);
    }

    /**
     * Registro independiente (Épica 7): a diferencia de {@link #createBasePerson},
     * la persona elige su usuario/contraseña en el momento y puede loguearse de
     * inmediato — no queda a la espera de que staff confirme el alta como en el
     * flujo de Prospect. {@code passwordHasheada} ya viene codificada por el
     * caller (mismo {@code PasswordEncoder} que usa AuthService), esta clase no
     * conoce el algoritmo de hashing.
     */
    @Transactional
    public Person createPersonWithCredentials(
            String firstName, String lastName, String email, String username, String passwordHasheada) {
        Person person = createBasePerson(firstName, lastName, email);
        person.setUsername(username);
        person.setPassword(passwordHasheada);
        return personRepository.save(person);
    }

    /**
     * Épica 1 Fase 2 (Login con Google): igual que {@link #createPersonWithCredentials} pero sin
     * password — la persona nunca elige una, entra siempre por Google. {@code username} es el
     * email (mismo criterio que el registro independiente, no hay campo de usuario separado).
     */
    @Transactional
    public Person createPersonWithGoogle(String firstName, String lastName, String email, String googleSubjectId) {
        Person person = createBasePerson(firstName, lastName, email);
        person.setUsername(email);
        person.setAuthProvider(AuthProvider.GOOGLE);
        person.setGoogleSubjectId(googleSubjectId);
        return personRepository.save(person);
    }
}
