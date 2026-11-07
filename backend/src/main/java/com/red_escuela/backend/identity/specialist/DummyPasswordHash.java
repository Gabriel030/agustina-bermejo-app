package com.red_escuela.backend.identity.specialist;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class DummyPasswordHash {

    private final String hash;

    public DummyPasswordHash(PasswordEncoder passwordEncoder) {
        this.hash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    public String value() {
        return hash;
    }
}
