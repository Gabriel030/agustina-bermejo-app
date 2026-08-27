package com.red_escuela.backend.service;

import com.red_escuela.backend.entity.Person;
import com.red_escuela.backend.entity.Role;
import com.red_escuela.backend.exception.RoleNotFoundException;
import com.red_escuela.backend.repository.RoleRepository;
import org.springframework.stereotype.Service;

/**
 * Épica 7: quien se registra de forma independiente no tiene rol hasta su
 * primera aprobación (carrera) o inscripción (curso) — dos flujos distintos
 * (ProgramEnrollmentApplicationService, MeEnrollmentController) necesitan
 * exactamente el mismo "otorgar ALUMNO si todavía no lo tiene", se centraliza
 * acá en vez de duplicarlo.
 */
@Service
public class PersonRoleGranter {

    private static final String ROLE_ALUMNO = "ALUMNO";

    private final RoleRepository roleRepository;

    public PersonRoleGranter(RoleRepository roleRepository) {
        this.roleRepository = roleRepository;
    }

    public void otorgarAlumnoSiNoLoTiene(Person person) {
        boolean yaLoTiene = person.getRoles().stream().anyMatch(r -> r.getName().equals(ROLE_ALUMNO));
        if (yaLoTiene) {
            return;
        }
        Role alumno = roleRepository.findByName(ROLE_ALUMNO).orElseThrow(() -> new RoleNotFoundException(ROLE_ALUMNO));
        person.getRoles().add(alumno);
    }
}
