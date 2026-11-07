package com.red_escuela.backend.identity.usecase.staff;

import com.red_escuela.backend.identity.exception.PersonNotFoundException;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.repository.PersonRepository;
import com.red_escuela.backend.identity.specialist.StaffRoleAssignmentPolicy;
import com.red_escuela.backend.identity.specialist.PasswordResetCodeRequester;
import com.red_escuela.backend.shared.UseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@UseCase
public class ResetStaffPasswordService implements ResetStaffPasswordUseCase {

    private static final Logger log = LoggerFactory.getLogger(ResetStaffPasswordService.class);

    private final PersonRepository personRepository;
    private final StaffRoleAssignmentPolicy staffRoleAssignmentPolicy;
    private final PasswordResetCodeRequester passwordResetCodeRequester;

    public ResetStaffPasswordService(PersonRepository personRepository,
                                     StaffRoleAssignmentPolicy staffRoleAssignmentPolicy,
                                     PasswordResetCodeRequester passwordResetCodeRequester) {
        this.personRepository = personRepository;
        this.staffRoleAssignmentPolicy = staffRoleAssignmentPolicy;
        this.passwordResetCodeRequester = passwordResetCodeRequester;
    }

    @Override
    public void resetPassword(Long personId) {
        Person person = personRepository.findById(personId)
                .orElseThrow(() -> new PersonNotFoundException(personId));
        String staffRole = staffRoleAssignmentPolicy.resolveStaffRole(person);
        if (staffRole == null) {
            throw new PersonNotFoundException(personId);
        }
        staffRoleAssignmentPolicy.requireCanAssign(staffRole);
        staffRoleAssignmentPolicy.requireNoEmailChangeInFlight(person);
        passwordResetCodeRequester.requestPasswordResetCode(person.emailValue());
        log.info("Reset de contraseña solicitado: personId={}", personId);
    }
}
