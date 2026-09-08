package com.red_escuela.backend.service;

import com.red_escuela.backend.identity.entity.Person;

public interface EmailService {

    void sendPasswordResetCode(Person person, String code);
}
