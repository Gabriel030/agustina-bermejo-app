package com.red_escuela.backend.identity.specialist;

import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.exception.InvalidCurrentPasswordException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class CurrentPasswordVerifier {

    private final PasswordEncoder passwordEncoder;

    public CurrentPasswordVerifier(PasswordEncoder passwordEncoder) {
        this.passwordEncoder = passwordEncoder;
    }

    public void requireMatches(Person person, String currentPassword) {
        if (currentPassword == null || person.getPassword() == null
                || !passwordEncoder.matches(currentPassword, person.getPassword())) {
            throw new InvalidCurrentPasswordException();
        }
    }
}
