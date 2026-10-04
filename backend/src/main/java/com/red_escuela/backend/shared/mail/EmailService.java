package com.red_escuela.backend.shared.mail;

import com.red_escuela.backend.identity.entity.Person;

public interface EmailService {

    void sendPasswordResetCode(Person person, String code);

    void sendEmailVerification(Person person, String verificationLink);
}
