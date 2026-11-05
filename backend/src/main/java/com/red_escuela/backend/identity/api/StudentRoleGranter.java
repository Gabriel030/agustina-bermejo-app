package com.red_escuela.backend.identity.api;

import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.entity.Role;
import com.red_escuela.backend.identity.exception.RoleNotFoundException;
import com.red_escuela.backend.identity.repository.RoleRepository;
import org.springframework.stereotype.Component;

@Component
public class StudentRoleGranter {
    private static final String ROLE_ALUMNO = "ALUMNO";

    private final RoleRepository roleRepository;

    public StudentRoleGranter(RoleRepository roleRepository) {
        this.roleRepository = roleRepository;
    }

    public void grantStudentRoleIfMissing(Person person) {
        boolean alreadyGranted = person.getRoles().stream().anyMatch(r -> r.getName().equals(ROLE_ALUMNO));
        if (alreadyGranted) {
            return;
        }
        Role studentRole = roleRepository.findByName(ROLE_ALUMNO).orElseThrow(() -> new RoleNotFoundException(ROLE_ALUMNO));
        person.getRoles().add(studentRole);
    }
}
