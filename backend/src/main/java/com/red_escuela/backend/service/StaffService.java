package com.red_escuela.backend.service;

import com.red_escuela.backend.dto.CreateStaffRequest;
import com.red_escuela.backend.dto.UpdateStaffRequest;
import com.red_escuela.backend.entity.Instructor;
import com.red_escuela.backend.entity.Person;
import com.red_escuela.backend.entity.Role;
import com.red_escuela.backend.exception.DniAlreadyExistsException;
import com.red_escuela.backend.exception.InvalidStaffRoleException;
import com.red_escuela.backend.exception.PersonNotFoundException;
import com.red_escuela.backend.exception.RoleNotFoundException;
import com.red_escuela.backend.repository.InstructorRepository;
import com.red_escuela.backend.repository.PersonRepository;
import com.red_escuela.backend.repository.RoleRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

/**
 * Alta y edición de personal (Docente/Preceptor). A diferencia del alumno no hay
 * flujo de prospecto — RF-AD-05 no lo describe así: dni/username se setean de una.
 */
@Service
public class StaffService {

    private static final Set<String> ALLOWED_ROLES = Set.of("DOCENTE", "PRECEPTOR");

    private final PersonRepository personRepository;
    private final InstructorRepository instructorRepository;
    private final RoleRepository roleRepository;
    private final PersonFactory personFactory;
    private final AuthService authService;

    public StaffService(
            PersonRepository personRepository,
            InstructorRepository instructorRepository,
            RoleRepository roleRepository,
            PersonFactory personFactory,
            AuthService authService) {
        this.personRepository = personRepository;
        this.instructorRepository = instructorRepository;
        this.roleRepository = roleRepository;
        this.personFactory = personFactory;
        this.authService = authService;
    }

    @Transactional
    public Person altaPersonal(CreateStaffRequest request) {
        String requestedRole = request.role();
        if (!ALLOWED_ROLES.contains(requestedRole)) {
            throw new InvalidStaffRoleException(requestedRole);
        }

        Person person = personFactory.crearPersonBase(request.nombre(), request.apellido(), request.email());
        person.setDni(request.dni());
        person.setUsername(request.dni());

        var address = person.getAddress();
        address.setStreet(request.street());
        address.setNumber(request.number());
        address.setFloor(request.floor());
        address.setCity(request.city());
        address.setState(request.state());
        address.setPostalCode(request.postalCode());
        address.setCountry(request.country());

        Role role = roleRepository.findByName(requestedRole)
                .orElseThrow(() -> new RoleNotFoundException(requestedRole));
        person.getRoles().add(role);

        try {
            personRepository.saveAndFlush(person);
        } catch (DataIntegrityViolationException e) {
            throw new DniAlreadyExistsException(request.dni());
        }

        if ("DOCENTE".equals(requestedRole)) {
            Instructor instructor = new Instructor();
            instructor.setPerson(person);
            instructorRepository.save(instructor);
        }

        authService.requestPasswordReset(person.getEmail());

        return person;
    }

    @Transactional
    public Person editarPersonal(Long personId, UpdateStaffRequest request) {
        Person person = personRepository.findById(personId)
                .orElseThrow(() -> new PersonNotFoundException(personId));

        if (request.email() != null) {
            person.setEmail(request.email());
        }
        if (request.telefono() != null) {
            person.setPhoneNumber(request.telefono());
        }

        var address = person.getAddress();
        if (request.street() != null) {
            address.setStreet(request.street());
        }
        if (request.number() != null) {
            address.setNumber(request.number());
        }
        if (request.floor() != null) {
            address.setFloor(request.floor());
        }
        if (request.city() != null) {
            address.setCity(request.city());
        }
        if (request.state() != null) {
            address.setState(request.state());
        }
        if (request.postalCode() != null) {
            address.setPostalCode(request.postalCode());
        }
        if (request.country() != null) {
            address.setCountry(request.country());
        }

        return personRepository.save(person);
    }
}
