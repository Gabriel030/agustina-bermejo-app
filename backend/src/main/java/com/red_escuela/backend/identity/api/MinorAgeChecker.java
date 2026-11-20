package com.red_escuela.backend.identity.api;

import com.red_escuela.backend.identity.policy.LegalAge;
import com.red_escuela.backend.shared.time.ApplicationTime;
import java.time.LocalDate;
import org.springframework.stereotype.Component;

@Component
public class MinorAgeChecker {

    public boolean isMinor(LocalDate birthDate) {
        return LegalAge.isMinor(birthDate, ApplicationTime.today());
    }
}
