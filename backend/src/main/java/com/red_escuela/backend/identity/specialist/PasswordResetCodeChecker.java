package com.red_escuela.backend.identity.specialist;

import com.red_escuela.backend.identity.entity.PasswordResetToken;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.exception.InvalidPasswordResetTokenException;
import com.red_escuela.backend.identity.repository.PasswordResetTokenRepository;
import com.red_escuela.backend.identity.repository.PersonRepository;
import com.red_escuela.backend.shared.time.ApplicationTime;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class PasswordResetCodeChecker {
    private final PersonRepository personRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final int accountMaxAttempts;
    private final long accountLockoutMinutes;

    public PasswordResetCodeChecker(PersonRepository personRepository, PasswordResetTokenRepository passwordResetTokenRepository,
                                    @Value("${auth.password-reset-lockout.max-attempts}") int accountMaxAttempts,
                                    @Value("${auth.password-reset-lockout.duration-minutes}") long accountLockoutMinutes) {
        this.personRepository = personRepository;
        this.passwordResetTokenRepository = passwordResetTokenRepository;
        this.accountMaxAttempts = accountMaxAttempts;
        this.accountLockoutMinutes = accountLockoutMinutes;
    }

    public void requireNotLocked(Person person) {
        if (person.getResetLockedUntil() != null && person.getResetLockedUntil().isAfter(ApplicationTime.now())) {
            throw new InvalidPasswordResetTokenException();
        }
    }

    public void clearFailedAttempts(Person person) {
        person.setResetFailedAttempts(0);
        personRepository.save(person);
    }

    public void registerFailedAttempt(Person person, PasswordResetToken resetToken) {
        int attempts = person.getResetFailedAttempts() + 1;
        if (attempts >= accountMaxAttempts) {
            person.setResetLockedUntil(ApplicationTime.now().plusMinutes(accountLockoutMinutes));
            person.setResetFailedAttempts(0);
            resetToken.setUsed(true);
            passwordResetTokenRepository.save(resetToken);
        } else {
            person.setResetFailedAttempts(attempts);
        }
        personRepository.save(person);
    }
}
