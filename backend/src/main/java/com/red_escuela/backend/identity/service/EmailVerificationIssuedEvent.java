package com.red_escuela.backend.identity.service;

import com.red_escuela.backend.identity.entity.Person;

public record EmailVerificationIssuedEvent(Person person, String verificationLink) {
}
