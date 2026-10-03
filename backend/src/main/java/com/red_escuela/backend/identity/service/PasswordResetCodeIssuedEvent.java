package com.red_escuela.backend.identity.service;

import com.red_escuela.backend.identity.entity.Person;

public record PasswordResetCodeIssuedEvent(Person person, String code) {
}
