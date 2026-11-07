package com.red_escuela.backend.identity.specialist;

import com.red_escuela.backend.identity.entity.ContactChangeType;
import com.red_escuela.backend.identity.entity.EmailVerificationToken;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.exception.EmailAlreadyExistsException;
import com.red_escuela.backend.identity.repository.PersonRepository;
import com.red_escuela.backend.shared.domain.EmailAddress;
import org.springframework.stereotype.Service;

@Service
public class PendingEmailChangeConfirmer {

    private final PersonRepository personRepository;
    private final ContactChangeRecorder contactChangeRecorder;

    public PendingEmailChangeConfirmer(PersonRepository personRepository, ContactChangeRecorder contactChangeRecorder) {
        this.personRepository = personRepository;
        this.contactChangeRecorder = contactChangeRecorder;
    }

    public void confirmIfPending(EmailVerificationToken token) {
        if (token.getNewEmail() == null) {
            return;
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
    }
}
