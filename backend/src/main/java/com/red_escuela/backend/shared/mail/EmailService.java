package com.red_escuela.backend.shared.mail;

public interface EmailService {

    void sendPasswordResetCode(MailRecipient recipient, String institutionName, String code);

    void sendEmailVerification(MailRecipient recipient, String institutionName, String verificationLink);

    void sendEmailChangeVerification(String recipientName, String newEmail, String verificationLink);

    void sendPasswordResetLockoutNotice(String recipientName, String email);

    void sendEmailChangeNotice(String recipientName, String previousEmail, String newEmail);
}
