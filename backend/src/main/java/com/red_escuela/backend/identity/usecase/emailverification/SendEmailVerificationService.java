package com.red_escuela.backend.identity.usecase.emailverification;

import com.red_escuela.backend.identity.service.EmailVerificationRequester;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class SendEmailVerificationService implements SendEmailVerificationUseCase {

    private final EmailVerificationRequester emailVerificationRequester;

    public SendEmailVerificationService(EmailVerificationRequester emailVerificationRequester) {
        this.emailVerificationRequester = emailVerificationRequester;
    }

    @Override
    public void sendVerification(Long studentId) {
        emailVerificationRequester.requestVerification(studentId);
    }
}
