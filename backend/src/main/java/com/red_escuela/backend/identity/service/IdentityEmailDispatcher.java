package com.red_escuela.backend.identity.service;

import com.red_escuela.backend.shared.mail.EmailService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class IdentityEmailDispatcher {
    private final EmailService emailService;

    public IdentityEmailDispatcher(EmailService emailService) {
        this.emailService = emailService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onPasswordResetCodeIssued(PasswordResetCodeIssuedEvent event) {
        emailService.sendPasswordResetCode(event.person(), event.code());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onEmailVerificationIssued(EmailVerificationIssuedEvent event) {
        emailService.sendEmailVerification(event.person(), event.verificationLink());
    }
}
