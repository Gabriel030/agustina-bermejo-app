package com.red_escuela.backend.shared.mail;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("test")
public class NoOpEmailService implements EmailService {

    @Override
    public void sendPasswordResetCode(MailRecipient recipient, String institutionName, String code) {
    }

    @Override
    public void sendEmailVerification(MailRecipient recipient, String institutionName, String verificationLink) {
    }

    @Override
    public void sendEmailChangeVerification(String recipientName, String newEmail, String verificationLink) {
    }

    @Override
    public void sendPasswordResetLockoutNotice(String recipientName, String email) {
    }

    @Override
    public void sendGuardianLegalConfirmation(String guardianName, String guardianEmail, String minorFirstName, String institutionName, String confirmationLink) {
    }

    @Override
    public void sendEmailChangeOwnerApproval(String recipientName, String currentEmail, String requestedEmail, String approvalLink, String cancellationLink) {
    }

    @Override
    public void sendEmailChangeNotice(String recipientName, String previousEmail, String newEmail, String cancellationLink) {
    }

    @Override
    public void sendEmailChangedNotice(String recipientName, String previousEmail, String newEmail) {
    }
}
