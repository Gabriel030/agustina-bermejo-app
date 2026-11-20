package com.red_escuela.backend.identity.specialist;

import com.red_escuela.backend.identity.entity.ContactChangeType;
import com.red_escuela.backend.identity.entity.EmailVerificationToken;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.exception.EmailAlreadyExistsException;
import com.red_escuela.backend.identity.repository.PasswordResetTokenRepository;
import com.red_escuela.backend.identity.repository.PersonRepository;
import com.red_escuela.backend.shared.domain.EmailAddress;
import com.red_escuela.backend.shared.exception.InvalidEmailVerificationTokenException;
import org.springframework.stereotype.Service;

@Service
public class PendingEmailChangeConfirmer {

    private final PersonRepository personRepository;
    private final ContactChangeRecorder contactChangeRecorder;
    private final SessionRevoker sessionRevoker;
    private final PasswordResetTokenRepository passwordResetTokenRepository;

    public PendingEmailChangeConfirmer(PersonRepository personRepository, ContactChangeRecorder contactChangeRecorder,
                                       SessionRevoker sessionRevoker,
                                       PasswordResetTokenRepository passwordResetTokenRepository) {
        this.personRepository = personRepository;
        this.contactChangeRecorder = contactChangeRecorder;
        this.sessionRevoker = sessionRevoker;
        this.passwordResetTokenRepository = passwordResetTokenRepository;
    }

    public void confirmIfPending(EmailVerificationToken token) {
        if (token.getNewEmail() == null) {
            return;
        }
        if (token.isAwaitingOwnerApproval()) {
            throw new InvalidEmailVerificationTokenException();
        }
        Person person = token.getPerson();
        EmailAddress newEmail = EmailAddress.of(token.getNewEmail());
        personRepository.findByEmail(newEmail)
                .filter(owner -> !owner.getId().equals(person.getId()))
                .ifPresent(owner -> {
                    throw new EmailAlreadyExistsException(token.getNewEmail());
                });
        String previousEmail = person.emailValue();
        person.setEmail(newEmail);
        contactChangeRecorder.recordChange(person.getId(), person.getId(), ContactChangeType.EMAIL_CHANGE_CONFIRMED,
                previousEmail, newEmail.value());
        sessionRevoker.revokeAllSessions(person);
        passwordResetTokenRepository.invalidateActiveCodes(person.getId());
        passwordResetTokenRepository.clearExchangeTokens(person.getId());
    }
}
