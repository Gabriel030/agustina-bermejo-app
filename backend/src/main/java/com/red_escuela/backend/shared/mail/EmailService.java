package com.red_escuela.backend.shared.mail;

public interface EmailService {

    void sendPasswordResetCode(MailRecipient recipient, String institutionName, String code);

    void sendEmailVerification(MailRecipient recipient, String institutionName, String verificationLink);

    void sendEmailChangeVerification(String recipientName, String newEmail, String verificationLink);

    void sendPasswordResetLockoutNotice(String recipientName, String email);

    void sendGuardianLegalConfirmation(String guardianName, String guardianEmail, String minorFirstName, String institutionName, String confirmationLink);

    void sendEmailChangeOwnerApproval(String recipientName, String currentEmail, String requestedEmail, String approvalLink, String cancellationLink);

    void sendEmailChangeNotice(String recipientName, String previousEmail, String newEmail, String cancellationLink);

    void sendEmailChangedNotice(String recipientName, String previousEmail, String newEmail);
}
