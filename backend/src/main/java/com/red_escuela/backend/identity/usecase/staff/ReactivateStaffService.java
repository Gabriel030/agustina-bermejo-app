package com.red_escuela.backend.identity.usecase.staff;

import com.red_escuela.backend.shared.domain.StatusType;
import com.red_escuela.backend.identity.exception.PersonNotFoundException;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.entity.Role;
import com.red_escuela.backend.identity.repository.PersonRepository;
import com.red_escuela.backend.identity.service.StaffRoleAssignmentPolicy;
import com.red_escuela.backend.shared.UseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@UseCase
public class ReactivateStaffService implements ReactivateStaffUseCase {

    private static final Logger log = LoggerFactory.getLogger(ReactivateStaffService.class);

    private final PersonRepository personRepository;
    private final StaffRoleAssignmentPolicy staffRoleAssignmentPolicy;

    public ReactivateStaffService(PersonRepository personRepository,
                                  StaffRoleAssignmentPolicy staffRoleAssignmentPolicy) {
        this.personRepository = personRepository;
        this.staffRoleAssignmentPolicy = staffRoleAssignmentPolicy;
    }

    @Override
    @Transactional
    public void reactivate(Long personId) {
        Person person = personRepository.findById(personId)
                .orElseThrow(() -> new PersonNotFoundException(personId));
        String staffRole = person.getRoles().stream()
                .map(Role::getName)
                .filter(StaffRoleAssignmentPolicy.STAFF_ROLES::contains)
                .findFirst()
                .orElseThrow(() -> new PersonNotFoundException(personId));
        staffRoleAssignmentPolicy.requireCanAssign(staffRole);
        if (person.getStatus() != StatusType.INACTIVE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "El miembro del personal no está dado de baja");
        }
        person.setStatus(StatusType.ACTIVE);
        personRepository.save(person);
        log.info("Personal reactivado: personId={}", personId);
    }
}
