package com.red_escuela.backend.identity.api;

import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.entity.Role;
import com.red_escuela.backend.identity.exception.PersonNotFoundException;
import com.red_escuela.backend.identity.policy.StaffVisibilityPolicy;
import com.red_escuela.backend.identity.repository.PersonRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
public class StaffRecordAccessChecker {

    private static final String ROLE_PREFIX = "ROLE_";

    private final PersonRepository personRepository;

    public StaffRecordAccessChecker(PersonRepository personRepository) {
        this.personRepository = personRepository;
    }

    @Transactional(readOnly = true)
    public void requireCanView(Long staffPersonId) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (staffPersonId.equals(Long.valueOf(authentication.getName()))) {
            return;
        }
        List<String> actorRoles = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(authority -> authority.startsWith(ROLE_PREFIX))
                .map(authority -> authority.substring(ROLE_PREFIX.length()))
                .toList();
        Person target = personRepository.findAllWithRolesByIdIn(List.of(staffPersonId)).stream()
                .findFirst()
                .orElseThrow(() -> new PersonNotFoundException(staffPersonId));
        List<String> targetRoles = target.getRoles().stream().map(Role::getName).toList();
        if (!StaffVisibilityPolicy.canView(actorRoles, targetRoles)) {
            throw new PersonNotFoundException(staffPersonId);
        }
    }
}
