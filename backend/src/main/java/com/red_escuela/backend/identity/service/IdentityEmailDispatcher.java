package com.red_escuela.backend.identity.service;

import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.events.PasswordResetCodeIssuedEvent;
import com.red_escuela.backend.identity.repository.PersonRepository;
import com.red_escuela.backend.shared.event.ProcessedEffectGuard;
import com.red_escuela.backend.shared.mail.EmailService;
import com.red_escuela.backend.shared.mail.MailRecipient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

@Component
public class IdentityEmailDispatcher {
    private static final Logger log = LoggerFactory.getLogger(IdentityEmailDispatcher.class);

    private final EmailService emailService;
    private final PersonRepository personRepository;
    private final ProcessedEffectGuard effectGuard;
    private final InstitutionNameResolver institutionNameResolver;

    public IdentityEmailDispatcher(EmailService emailService, PersonRepository personRepository, ProcessedEffectGuard effectGuard,
            InstitutionNameResolver institutionNameResolver) {
        this.emailService = emailService;
        this.personRepository = personRepository;
        this.effectGuard = effectGuard;
        this.institutionNameResolver = institutionNameResolver;
    }

    @ApplicationModuleListener
    public void onPasswordResetCodeIssued(PasswordResetCodeIssuedEvent event) {
        effectGuard.runEffectOnce(event, "IdentityEmailDispatcher.onPasswordResetCodeIssued", () ->
                personRepository.findById(event.personId()).ifPresentOrElse(
                        person -> {
                            if (hasNoEmail(person)) {
                                log.warn("Password reset email skipped, person has no email: personId={}", event.personId());
                                return;
                            }
                            emailService.sendPasswordResetCode(recipientOf(person), institutionNameResolver.resolveInstitutionName(), event.code());
                        },
                        () -> log.warn("Password reset email skipped, person not found: personId={}", event.personId())));
    }

    @ApplicationModuleListener
    public void onEmailVerificationIssued(EmailVerificationIssuedEvent event) {
        effectGuard.runEffectOnce(event, "IdentityEmailDispatcher.onEmailVerificationIssued", () ->
                personRepository.findById(event.personId()).ifPresentOrElse(
                        person -> {
                            if (hasNoEmail(person)) {
                                log.warn("Verification email skipped, person has no email: personId={}", event.personId());
                                return;
                            }
                            emailService.sendEmailVerification(recipientOf(person), institutionNameResolver.resolveInstitutionName(), event.verificationLink());
                        },
                        () -> log.warn("Verification email skipped, person not found: personId={}", event.personId())));
    }

    @ApplicationModuleListener
    public void onEmailChangeRequested(EmailChangeRequestedEvent event) {
        effectGuard.runEffectOnce(event, "IdentityEmailDispatcher.onEmailChangeRequested", () ->
                personRepository.findById(event.personId()).ifPresentOrElse(person -> {
                    emailService.sendEmailChangeVerification(person.getName(), event.newEmail(), event.verificationLink());
                    if (event.previousEmail() != null) {
                        emailService.sendEmailChangeNotice(person.getName(), event.previousEmail(), event.newEmail());
                    }
                }, () -> log.warn("Email change mail skipped, person not found: personId={}", event.personId())));
    }

    private boolean hasNoEmail(Person person) {
        String email = person.emailValue();
        return email == null || email.isBlank();
    }

    private MailRecipient recipientOf(Person person) {
        String email = person.emailValue();
        return new MailRecipient(person.getId(), displayNameOf(person, email), email);
    }

    private String displayNameOf(Person person, String email) {
        if (person.getName() != null && !person.getName().isBlank()) {
            return person.getName();
        }
        if (person.getUsername() != null && !person.getUsername().isBlank()) {
            return person.getUsername();
        }
        return email == null ? "" : email.split("@", 2)[0];
    }
}
