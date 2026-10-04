package com.red_escuela.backend.identity.usecase.staff;

import com.red_escuela.backend.identity.exception.PersonNotFoundException;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.entity.Role;
import com.red_escuela.backend.identity.repository.PersonRepository;
import com.red_escuela.backend.identity.service.StaffRoleAssignmentPolicy;
import com.red_escuela.backend.identity.usecase.auth.RequestPasswordResetUseCase;
import com.red_escuela.backend.shared.UseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@UseCase
public class ResetStaffPasswordService implements ResetStaffPasswordUseCase {

    private static final Logger log = LoggerFactory.getLogger(ResetStaffPasswordService.class);

    private final PersonRepository personRepository;
    private final RequestPasswordResetUseCase requestPasswordResetUseCase;

    public ResetStaffPasswordService(PersonRepository personRepository,
                                     RequestPasswordResetUseCase requestPasswordResetUseCase) {
        this.personRepository = personRepository;
        this.requestPasswordResetUseCase = requestPasswordResetUseCase;
    }

    @Override
    public void resetPassword(Long personId) {
        Person person = personRepository.findById(personId)
                .orElseThrow(() -> new PersonNotFoundException(personId));
        boolean isStaff = person.getRoles().stream()
                .map(Role::getName)
                .anyMatch(StaffRoleAssignmentPolicy.STAFF_ROLES::contains);
        if (!isStaff) {
            throw new PersonNotFoundException(personId);
        }
        requestPasswordResetUseCase.requestReset(person.emailValue());
        log.info("Reset de contraseña solicitado: personId={}", personId);
    }
}
