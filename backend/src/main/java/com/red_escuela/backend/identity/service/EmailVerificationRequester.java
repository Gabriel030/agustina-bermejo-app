package com.red_escuela.backend.identity.service;

import com.red_escuela.backend.identity.api.EmailVerificationSender;
import org.springframework.stereotype.Service;

@Service
public class EmailVerificationRequester {
    private final EmailVerificationSender emailVerificationSender;

    public EmailVerificationRequester(EmailVerificationSender emailVerificationSender) {
        this.emailVerificationSender = emailVerificationSender;
    }

    public void requestVerification(Long studentId) {
        emailVerificationSender.sendVerificationToStudent(studentId);
    }
}
