package com.red_escuela.backend.identity.usecase.staff;

import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.repository.EmailVerificationTokenRepository;
import com.red_escuela.backend.shared.UseCase;
import org.springframework.transaction.annotation.Transactional;

@UseCase
public class CheckStaffEmailChangePendingService implements CheckStaffEmailChangePendingUseCase {

    private final EmailVerificationTokenRepository tokenRepository;

    public CheckStaffEmailChangePendingService(EmailVerificationTokenRepository tokenRepository) {
        this.tokenRepository = tokenRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasEmailChangePending(Person person) {
        return tokenRepository.existsByPersonAndNewEmailIsNotNull(person);
    }
}
