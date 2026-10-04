package com.red_escuela.backend.identity.service;

import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.entity.Role;
import com.red_escuela.backend.identity.exception.RoleNotFoundException;
import com.red_escuela.backend.identity.repository.RoleRepository;
import org.springframework.stereotype.Service;

@Service
public class PersonRoleGranter {
    private static final String ROLE_ALUMNO = "ALUMNO";

    private final RoleRepository roleRepository;

    public PersonRoleGranter(RoleRepository roleRepository) {
        this.roleRepository = roleRepository;
    }

    public void grantStudentRoleIfMissing(Person person) {
        boolean yaLoTiene = person.getRoles().stream().anyMatch(r -> r.getName().equals(ROLE_ALUMNO));
        if (yaLoTiene) {
            return;
        }
        Role alumno = roleRepository.findByName(ROLE_ALUMNO).orElseThrow(() -> new RoleNotFoundException(ROLE_ALUMNO));
        person.getRoles().add(alumno);
    }
}
