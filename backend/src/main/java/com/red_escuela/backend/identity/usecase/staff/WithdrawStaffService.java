package com.red_escuela.backend.identity.usecase.staff;

import com.red_escuela.backend.shared.domain.StatusType;
import com.red_escuela.backend.identity.exception.PersonNotFoundException;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.entity.Role;
import com.red_escuela.backend.identity.repository.PersonRepository;
import com.red_escuela.backend.identity.service.StaffRoleAssignmentPolicy;
import com.red_escuela.backend.identity.usecase.person.RevokeAllSessionsUseCase;
import com.red_escuela.backend.shared.UseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

@UseCase
public class WithdrawStaffService implements WithdrawStaffUseCase {

    private static final Logger log = LoggerFactory.getLogger(WithdrawStaffService.class);

    private final PersonRepository personRepository;
    private final StaffRoleAssignmentPolicy staffRoleAssignmentPolicy;
    private final RevokeAllSessionsUseCase revokeAllSessionsUseCase;

    public WithdrawStaffService(PersonRepository personRepository,
                                StaffRoleAssignmentPolicy staffRoleAssignmentPolicy,
                                RevokeAllSessionsUseCase revokeAllSessionsUseCase) {
        this.personRepository = personRepository;
        this.staffRoleAssignmentPolicy = staffRoleAssignmentPolicy;
        this.revokeAllSessionsUseCase = revokeAllSessionsUseCase;
    }

    @Override
    @Transactional
    public void withdraw(Long personId) {
        Person person = personRepository.findById(personId)
                .orElseThrow(() -> new PersonNotFoundException(personId));
        String staffRole = staffRole(person);
        staffRoleAssignmentPolicy.requireCanAssign(staffRole);
        person.setStatus(StatusType.INACTIVE);
        personRepository.save(person);
        revokeAllSessionsUseCase.revokeAll(person);
        log.info("Personal dado de baja: personId={}, role={}", personId, staffRole);
    }

    private String staffRole(Person person) {
        return person.getRoles().stream()
                .map(Role::getName)
                .filter(StaffRoleAssignmentPolicy.STAFF_ROLES::contains)
                .findFirst()
                .orElseThrow(() -> new PersonNotFoundException(person.getId()));
    }
}
