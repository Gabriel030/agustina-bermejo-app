package com.red_escuela.backend.service;

import com.red_escuela.backend.entity.Address;
import com.red_escuela.backend.entity.AuthProvider;
import com.red_escuela.backend.entity.Person;
import com.red_escuela.backend.entity.StatusType;
import com.red_escuela.backend.repository.AddressRepository;
import com.red_escuela.backend.repository.PersonRepository;
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
    public Person crearPersonBase(String nombre, String apellido, String email) {
        Address address = new Address();
        addressRepository.save(address);

        Person person = new Person();
        person.setName(nombre);
        person.setLastname(apellido);
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
     * Registro independiente (Épica 7): a diferencia de {@link #crearPersonBase},
     * la persona elige su usuario/contraseña en el momento y puede loguearse de
     * inmediato — no queda a la espera de que staff confirme el alta como en el
     * flujo de Prospect. {@code passwordHasheada} ya viene codificada por el
     * caller (mismo {@code PasswordEncoder} que usa AuthService), esta clase no
     * conoce el algoritmo de hashing.
     */
    @Transactional
    public Person crearPersonConCredenciales(
            String nombre, String apellido, String email, String username, String passwordHasheada) {
        Person person = crearPersonBase(nombre, apellido, email);
        person.setUsername(username);
        person.setPassword(passwordHasheada);
        return personRepository.save(person);
    }
}
