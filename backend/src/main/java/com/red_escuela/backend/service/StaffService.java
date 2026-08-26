package com.red_escuela.backend.service;

import com.red_escuela.backend.dto.CreateStaffRequest;
import com.red_escuela.backend.dto.UpdateStaffRequest;
import com.red_escuela.backend.entity.Instructor;
import com.red_escuela.backend.entity.Person;
import com.red_escuela.backend.entity.Role;
import com.red_escuela.backend.exception.DniAlreadyExistsException;
import com.red_escuela.backend.exception.InvalidStaffRoleException;
import com.red_escuela.backend.exception.PersonNotFoundException;
import com.red_escuela.backend.exception.RoleNotAssignableByActorException;
import com.red_escuela.backend.exception.RoleNotFoundException;
import com.red_escuela.backend.repository.InstructorRepository;
import com.red_escuela.backend.repository.PersonRepository;
import com.red_escuela.backend.repository.RoleRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Set;

/**
 * Alta y edición de personal (Docente/Preceptor/Administrativo). A diferencia del alumno no
 * hay flujo de prospecto — RF-AD-05 no lo describe así: dni/username se setean de una.
 *
 * Reparto de RF-AD-05/RF-DI-04: el Administrativo da de alta Docente/Preceptor, el Director
 * da de alta Administrativo — cada actor solo puede crear/editar los roles que la spec le
 * asigna, verificado acá (no en el @PreAuthorize del controller, que solo exige "alguno de
 * los dos roles" — la regla de negocio de "cuál rol exactamente" vive en el service).
 */
@Service
public class StaffService {

    private static final Set<String> ALLOWED_ROLES = Set.of("DOCENTE", "PRECEPTOR", "ADMINISTRATIVO");

    private static final Map<String, Set<String>> ROLES_QUE_PUEDE_CREAR = Map.of(
            "ADMINISTRATIVO", Set.of("DOCENTE", "PRECEPTOR"),
            "DIRECTOR", Set.of("ADMINISTRATIVO"));

    private final PersonRepository personRepository;
    private final InstructorRepository instructorRepository;
    private final RoleRepository roleRepository;
    private final PersonFactory personFactory;
    private final AuthService authService;
    private final CurrentPersonService currentPersonService;

    public StaffService(
            PersonRepository personRepository,
            InstructorRepository instructorRepository,
            RoleRepository roleRepository,
            PersonFactory personFactory,
            AuthService authService,
            CurrentPersonService currentPersonService) {
        this.personRepository = personRepository;
        this.instructorRepository = instructorRepository;
        this.roleRepository = roleRepository;
        this.personFactory = personFactory;
        this.authService = authService;
        this.currentPersonService = currentPersonService;
    }

    @Transactional
    public Person altaPersonal(CreateStaffRequest request) {
        String requestedRole = request.role();
        if (!ALLOWED_ROLES.contains(requestedRole)) {
            throw new InvalidStaffRoleException(requestedRole);
        }
        exigirPuedeAsignar(requestedRole);

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
        exigirPuedeAsignar(resolveStaffRole(person));

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

    // El @PreAuthorize del controller solo exige "Administrativo o Director" — cuál de los
    // dos puede tocar cuál rol puntual es la regla de negocio real (RF-AD-05/RF-DI-04).
    private void exigirPuedeAsignar(String targetRole) {
        // Set.of(...).contains(null) tira NPE (no false) — un target sin rol de staff
        // reconocido nunca está autorizado, se corta acá antes de tocar los sets.
        if (targetRole == null) {
            throw new RoleNotAssignableByActorException("desconocido");
        }
        Person actor = currentPersonService.resolveCurrentPerson();
        boolean autorizado = actor.getRoles().stream()
                .map(Role::getName)
                .anyMatch(actorRole -> ROLES_QUE_PUEDE_CREAR.getOrDefault(actorRole, Set.of()).contains(targetRole));
        if (!autorizado) {
            throw new RoleNotAssignableByActorException(targetRole);
        }
    }

    private String resolveStaffRole(Person person) {
        return person.getRoles().stream()
                .map(Role::getName)
                .filter(ALLOWED_ROLES::contains)
                .findFirst()
                .orElse(null);
    }
}
