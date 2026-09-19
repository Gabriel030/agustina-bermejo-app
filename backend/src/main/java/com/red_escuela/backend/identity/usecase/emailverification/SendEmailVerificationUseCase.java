package com.red_escuela.backend.identity.usecase.emailverification;

public interface SendEmailVerificationUseCase {

    void sendVerification(Long studentId);
}
