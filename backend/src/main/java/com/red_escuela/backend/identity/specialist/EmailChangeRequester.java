package com.red_escuela.backend.identity.specialist;

import com.red_escuela.backend.identity.entity.ContactChangeType;
import com.red_escuela.backend.identity.entity.EmailVerificationToken;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.exception.EmailAlreadyExistsException;
import com.red_escuela.backend.identity.exception.StaffPasswordResetBlockedException;
import com.red_escuela.backend.identity.repository.EmailVerificationTokenRepository;
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
public class EmailChangeRequester {

    private static final long EXPIRATION_HOURS = 48;

    private final PersonRepository personRepository;
    private final EmailVerificationTokenRepository tokenRepository;
    private final ContactChangeRecorder contactChangeRecorder;
    private final ApplicationEventPublisher eventPublisher;
    private final CurrentPasswordVerifier currentPasswordVerifier;
    private final CurrentPersonService currentPersonService;
    private final String frontendBaseUrl;
    private final SecureRandom secureRandom = new SecureRandom();

    public EmailChangeRequester(PersonRepository personRepository, EmailVerificationTokenRepository tokenRepository,
                                ContactChangeRecorder contactChangeRecorder, ApplicationEventPublisher eventPublisher,
                                CurrentPasswordVerifier currentPasswordVerifier, CurrentPersonService currentPersonService,
                                @Value("${app.frontend.base-url}") String frontendBaseUrl) {
        this.personRepository = personRepository;
        this.tokenRepository = tokenRepository;
        this.contactChangeRecorder = contactChangeRecorder;
        this.eventPublisher = eventPublisher;
        this.currentPasswordVerifier = currentPasswordVerifier;
        this.currentPersonService = currentPersonService;
        this.frontendBaseUrl = frontendBaseUrl;
    }

    public void requestOwnChange(Person person, String requestedEmail, String currentPassword) {
        if (isDifferentFromCurrent(person, requestedEmail)) {
            currentPasswordVerifier.requireMatches(person, currentPassword);
            requestChange(person, person.getId(), requestedEmail);
        }
    }

    public void requestStaffChange(Person target, String requestedEmail) {
        if (isDifferentFromCurrent(target, requestedEmail)) {
            requestChange(target, currentPersonService.resolveCurrentPerson().getId(), requestedEmail);
        }
    }

    public void requireNoEmailChangeInFlight(Person person) {
        if (tokenRepository.existsByPersonAndNewEmailIsNotNull(person)
                || contactChangeRecorder.hasRecentEmailChange(person.getId())) {
            throw new StaffPasswordResetBlockedException(person.getId());
        }
    }

    boolean isDifferentFromCurrent(Person person, String requestedEmail) {
        return person.getEmail() == null || !person.getEmail().equals(EmailAddress.of(requestedEmail));
    }

    void requestChange(Person person, Long actorPersonId, String requestedEmail) {
        EmailAddress newEmail = EmailAddress.of(requestedEmail);
        personRepository.findByEmail(newEmail)
                .filter(owner -> !owner.getId().equals(person.getId()))
                .ifPresent(owner -> {
                    throw new EmailAlreadyExistsException(requestedEmail);
                });
        if (tokenRepository.existsByNewEmailAndPersonNot(newEmail.value(), person)) {
            throw new EmailAlreadyExistsException(requestedEmail);
        }

        tokenRepository.deleteByPerson(person);
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        String clearToken = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        EmailVerificationToken token = new EmailVerificationToken();
        token.setPerson(person);
        token.setNewEmail(newEmail.value());
        token.setTokenHash(TokenHashUtil.sha256Hex(clearToken));
        token.setExpiresAt(ApplicationTime.now().plusHours(EXPIRATION_HOURS));
        tokenRepository.save(token);

        String previousEmail = person.emailValue();
        contactChangeRecorder.recordChange(person.getId(), actorPersonId, ContactChangeType.EMAIL_CHANGE_REQUESTED,
                previousEmail, newEmail.value());
        eventPublisher.publishEvent(EmailChangeRequestedEvent.requestedFor(person.getId(), previousEmail,
                newEmail.value(), frontendBaseUrl + "/verificar-email?token=" + clearToken));
    }
}
