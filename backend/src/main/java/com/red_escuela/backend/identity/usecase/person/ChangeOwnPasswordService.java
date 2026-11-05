package com.red_escuela.backend.identity.usecase.person;

import com.red_escuela.backend.identity.dto.ChangePasswordRequest;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.exception.InvalidCurrentPasswordException;
import com.red_escuela.backend.identity.repository.PersonRepository;
import com.red_escuela.backend.identity.service.CurrentPersonService;
import com.red_escuela.backend.identity.service.PasswordPolicyValidator;
import com.red_escuela.backend.identity.service.SessionRevoker;
import com.red_escuela.backend.shared.UseCase;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

@UseCase
public class ChangeOwnPasswordService implements ChangeOwnPasswordUseCase {

    private final PersonRepository personRepository;
    private final CurrentPersonService currentPersonService;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicyValidator passwordPolicyValidator;
    private final SessionRevoker sessionRevoker;

    public ChangeOwnPasswordService(PersonRepository personRepository,
                                    CurrentPersonService currentPersonService,
                                    PasswordEncoder passwordEncoder,
                                    PasswordPolicyValidator passwordPolicyValidator,
                                    SessionRevoker sessionRevoker) {
        this.personRepository = personRepository;
        this.currentPersonService = currentPersonService;
        this.passwordEncoder = passwordEncoder;
        this.passwordPolicyValidator = passwordPolicyValidator;
        this.sessionRevoker = sessionRevoker;
    }

    @Override
    @Transactional
    public void changePassword(ChangePasswordRequest request) {
        Person person = currentPersonService.resolveCurrentPerson();

        if (!passwordEncoder.matches(request.currentPassword(), person.getPassword())) {
            throw new InvalidCurrentPasswordException();
        }

        passwordPolicyValidator.validate(request.newPassword());

        person.setPassword(passwordEncoder.encode(request.newPassword()));
        personRepository.save(person);

        sessionRevoker.revokeAllSessions(person);
    }
}
