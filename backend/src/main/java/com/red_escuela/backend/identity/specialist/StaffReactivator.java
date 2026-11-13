package com.red_escuela.backend.identity.specialist;

import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.entity.Role;
import com.red_escuela.backend.identity.exception.PersonNotFoundException;
import com.red_escuela.backend.identity.infrastructure.AccountAccessStateCache;
import com.red_escuela.backend.identity.repository.PersonRepository;
import com.red_escuela.backend.shared.domain.StatusType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Component
public class StaffReactivator {

    private static final Logger log = LoggerFactory.getLogger(StaffReactivator.class);

    private final PersonRepository personRepository;
    private final StaffRoleAssignmentPolicy staffRoleAssignmentPolicy;
    private final AccountAccessStateCache accountAccessStateCache;

    public StaffReactivator(PersonRepository personRepository,
                             StaffRoleAssignmentPolicy staffRoleAssignmentPolicy,
                             AccountAccessStateCache accountAccessStateCache) {
        this.personRepository = personRepository;
        this.staffRoleAssignmentPolicy = staffRoleAssignmentPolicy;
        this.accountAccessStateCache = accountAccessStateCache;
    }

    @Transactional
    public void reactivate(Long personId) {
        Person person = personRepository.findById(personId)
                .orElseThrow(() -> new PersonNotFoundException(personId));
        String staffRole = person.getRoles().stream()
                .map(Role::getName)
                .filter(StaffRoleAssignmentPolicy.STAFF_ROLES::contains)
                .findFirst()
                .orElseThrow(() -> new PersonNotFoundException(personId));
        staffRoleAssignmentPolicy.requireManageableOrNotFound(personId, staffRole);
        if (person.getStatus() != StatusType.INACTIVE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "El miembro del personal no está dado de baja");
        }
        person.setStatus(StatusType.ACTIVE);
        personRepository.save(person);
        accountAccessStateCache.evictNowAndAfterCommit(personId);
        log.info("Personal reactivado: personId={}", personId);
    }
}
