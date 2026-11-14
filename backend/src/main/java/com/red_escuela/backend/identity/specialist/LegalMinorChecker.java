package com.red_escuela.backend.identity.specialist;

import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.policy.LegalAge;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;

@Service
public class LegalMinorChecker {

    private final Clock clock;

    public LegalMinorChecker(Clock clock) {
        this.clock = clock;
    }

    public boolean isMinor(LocalDate birthDate) {
        return birthDate != null && LegalAge.isMinor(birthDate, LocalDate.now(clock));
    }

    public boolean isMinor(Person person) {
        return isMinor(person.getBirthDate());
    }
}
