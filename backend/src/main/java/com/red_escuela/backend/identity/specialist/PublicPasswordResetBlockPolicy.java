package com.red_escuela.backend.identity.specialist;

import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.exception.InvalidPasswordResetTokenException;
import com.red_escuela.backend.identity.repository.EmailVerificationTokenRepository;
import org.springframework.stereotype.Service;

@Service
public class PublicPasswordResetBlockPolicy {

    private final EmailVerificationTokenRepository tokenRepository;
    private final ContactChangeRecorder contactChangeRecorder;

    public PublicPasswordResetBlockPolicy(EmailVerificationTokenRepository tokenRepository,
                                          ContactChangeRecorder contactChangeRecorder) {
        this.tokenRepository = tokenRepository;
        this.contactChangeRecorder = contactChangeRecorder;
    }

    public boolean emailChangeInFlight(Person person) {
        return tokenRepository.existsByPersonAndNewEmailIsNotNull(person)
                || contactChangeRecorder.hasRecentEmailChange(person.getId());
    }

    public boolean blocks(Person person) {
        return emailChangeInFlight(person) && !changeStartedByOwner(person);
    }

    public void requireNotBlocked(Person person) {
        if (blocks(person)) {
            throw new InvalidPasswordResetTokenException();
        }
    }

    private boolean changeStartedByOwner(Person person) {
        return contactChangeRecorder.lastEmailChangeRequesterId(person.getId())
                .filter(person.getId()::equals)
                .isPresent();
    }
}
