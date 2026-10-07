package com.red_escuela.backend.identity.service;

import com.red_escuela.backend.identity.repository.PersonRepository;
import com.red_escuela.backend.shared.mail.EmailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

@Component
public class IdentityEmailDispatcher {
    private static final Logger log = LoggerFactory.getLogger(IdentityEmailDispatcher.class);

    private final EmailService emailService;
    private final PersonRepository personRepository;

    public IdentityEmailDispatcher(EmailService emailService, PersonRepository personRepository) {
        this.emailService = emailService;
        this.personRepository = personRepository;
    }

    @ApplicationModuleListener
    public void onPasswordResetCodeIssued(PasswordResetCodeIssuedEvent event) {
        personRepository.findById(event.personId()).ifPresentOrElse(
                person -> emailService.sendPasswordResetCode(person, event.code()),
                () -> log.warn("Password reset email skipped, person not found: personId={}", event.personId()));
    }

    @ApplicationModuleListener
    public void onEmailVerificationIssued(EmailVerificationIssuedEvent event) {
        personRepository.findById(event.personId()).ifPresentOrElse(
                person -> emailService.sendEmailVerification(person, event.verificationLink()),
                () -> log.warn("Verification email skipped, person not found: personId={}", event.personId()));
    }
}
