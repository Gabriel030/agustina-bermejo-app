package com.red_escuela.backend.service;

import com.red_escuela.backend.entity.Person;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("test")
public class NoOpEmailService implements EmailService {

    @Override
    public void sendPasswordResetCode(Person person, String code) {
    }
}
