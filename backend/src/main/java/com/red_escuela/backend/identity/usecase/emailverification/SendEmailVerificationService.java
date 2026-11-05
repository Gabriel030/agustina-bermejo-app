package com.red_escuela.backend.identity.usecase.emailverification;

import com.red_escuela.backend.identity.api.EmailVerificationSender;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class SendEmailVerificationService implements SendEmailVerificationUseCase {

    private final EmailVerificationSender emailVerificationSender;

    public SendEmailVerificationService(EmailVerificationSender emailVerificationSender) {
        this.emailVerificationSender = emailVerificationSender;
    }

    @Override
    public void sendVerification(Long studentId) {
        emailVerificationSender.sendVerificationToStudent(studentId);
    }
}
