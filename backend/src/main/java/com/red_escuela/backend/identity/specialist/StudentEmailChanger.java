package com.red_escuela.backend.identity.specialist;

import com.red_escuela.backend.identity.entity.ContactChangeType;
import com.red_escuela.backend.identity.entity.EmailVerificationToken;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.events.StudentEmailChangedEvent;
import com.red_escuela.backend.identity.exception.EmailAlreadyExistsException;
import com.red_escuela.backend.identity.exception.StudentEmailChangeGoogleAccountException;
import com.red_escuela.backend.identity.repository.EmailVerificationTokenRepository;
import com.red_escuela.backend.identity.repository.PasswordResetTokenRepository;
import com.red_escuela.backend.identity.repository.PersonRepository;
import com.red_escuela.backend.identity.util.TokenHashUtil;
import com.red_escuela.backend.shared.domain.EmailAddress;
import com.red_escuela.backend.shared.time.ApplicationTime;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.Base64;

@Service
public class StudentEmailChanger {

    private static final long EXPIRATION_HOURS = 48;

    private final PersonRepository personRepository;
    private final EmailVerificationTokenRepository tokenRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final ContactChangeRecorder contactChangeRecorder;
    private final SessionRevoker sessionRevoker;
    private final ApplicationEventPublisher eventPublisher;
    private final String frontendBaseUrl;
    private final SecureRandom secureRandom = new SecureRandom();

    public StudentEmailChanger(PersonRepository personRepository, EmailVerificationTokenRepository tokenRepository,
                               PasswordResetTokenRepository passwordResetTokenRepository,
                               ContactChangeRecorder contactChangeRecorder, SessionRevoker sessionRevoker,
                               ApplicationEventPublisher eventPublisher,
                               @Value("${app.frontend.base-url}") String frontendBaseUrl) {
        this.personRepository = personRepository;
        this.tokenRepository = tokenRepository;
        this.passwordResetTokenRepository = passwordResetTokenRepository;
        this.contactChangeRecorder = contactChangeRecorder;
        this.sessionRevoker = sessionRevoker;
        this.eventPublisher = eventPublisher;
        this.frontendBaseUrl = frontendBaseUrl;
    }

    public void changeByStaff(Long studentId, Person person, String requestedEmail, Long actorPersonId) {
        EmailAddress newEmail = EmailAddress.of(requestedEmail.strip());
        if (newEmail.equals(person.getEmail())) {
            return;
        }
        if (!person.isEmailEditableByStaff()) {
            throw new StudentEmailChangeGoogleAccountException(studentId);
        }
        requireAvailable(person, newEmail);
        String previousEmail = person.emailValue();
        tokenRepository.deleteByPerson(person);
        String clearToken = newClearToken();
        EmailVerificationToken token = new EmailVerificationToken();
        token.setPerson(person);
        token.setTokenHash(TokenHashUtil.sha256Hex(clearToken));
        token.setExpiresAt(ApplicationTime.now().plusHours(EXPIRATION_HOURS));
        tokenRepository.save(token);
        person.setEmail(newEmail);
        person.setEmailVerified(false);
        personRepository.save(person);
        contactChangeRecorder.recordChange(person.getId(), actorPersonId, ContactChangeType.EMAIL_CHANGE_REQUESTED,
                previousEmail, newEmail.value());
        contactChangeRecorder.recordChange(person.getId(), actorPersonId, ContactChangeType.EMAIL_CHANGE_CONFIRMED,
                previousEmail, newEmail.value());
        sessionRevoker.revokeAllSessions(person);
        passwordResetTokenRepository.invalidateActiveCodes(person.getId());
        passwordResetTokenRepository.clearExchangeTokens(person.getId());
        eventPublisher.publishEvent(StudentEmailChangedEvent.changedFor(person.getId(), previousEmail,
                newEmail.value(), frontendBaseUrl + "/verificar-email?token=" + clearToken));
    }

    private void requireAvailable(Person person, EmailAddress newEmail) {
        personRepository.findByEmail(newEmail)
                .filter(owner -> !owner.getId().equals(person.getId()))
                .ifPresent(owner -> {
                    throw new EmailAlreadyExistsException(newEmail.value());
                });
        if (tokenRepository.existsByNewEmailAndPersonNot(newEmail.value(), person)) {
            throw new EmailAlreadyExistsException(newEmail.value());
        }
    }

    private String newClearToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
