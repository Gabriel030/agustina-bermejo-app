package com.red_escuela.backend.identity.service;

import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.exception.InvalidCurrentPasswordException;
import com.red_escuela.backend.identity.repository.PersonRepository;
import com.red_escuela.backend.identity.specialist.CurrentPersonService;
import com.red_escuela.backend.identity.specialist.IdentityAuditRecorder;
import com.red_escuela.backend.identity.specialist.PasswordPolicyValidator;
import com.red_escuela.backend.identity.specialist.SessionRevoker;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OwnPasswordChangeTransaction {
    private final PersonRepository personRepository;
    private final CurrentPersonService currentPersonService;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicyValidator passwordPolicyValidator;
    private final SessionRevoker sessionRevoker;
    private final IdentityAuditRecorder identityAuditRecorder;

    public OwnPasswordChangeTransaction(PersonRepository personRepository,
                                        CurrentPersonService currentPersonService,
                                        PasswordEncoder passwordEncoder,
                                        PasswordPolicyValidator passwordPolicyValidator,
                                        SessionRevoker sessionRevoker,
                                        IdentityAuditRecorder identityAuditRecorder) {
        this.personRepository = personRepository;
        this.currentPersonService = currentPersonService;
        this.passwordEncoder = passwordEncoder;
        this.passwordPolicyValidator = passwordPolicyValidator;
        this.sessionRevoker = sessionRevoker;
        this.identityAuditRecorder = identityAuditRecorder;
    }

    @Transactional
    public void change(String currentPassword, String newPassword) {
        Person person = currentPersonService.resolveCurrentPerson();

        if (!passwordEncoder.matches(currentPassword, person.getPassword())) {
            throw new InvalidCurrentPasswordException();
        }

        passwordPolicyValidator.validate(newPassword);

        person.setPassword(passwordEncoder.encode(newPassword));
        personRepository.save(person);

        sessionRevoker.revokeAllSessions(person);
        identityAuditRecorder.recordPasswordChanged(person);
    }
}
