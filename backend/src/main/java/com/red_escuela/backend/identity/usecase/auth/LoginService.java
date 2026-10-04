package com.red_escuela.backend.identity.usecase.auth;

import com.red_escuela.backend.shared.domain.StatusType;
import com.red_escuela.backend.identity.entity.LoginAttempt;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.exception.InvalidCredentialsException;
import com.red_escuela.backend.identity.repository.LoginAttemptRepository;
import com.red_escuela.backend.identity.repository.PersonRepository;
import com.red_escuela.backend.identity.service.AuthTokenIssuer;
import com.red_escuela.backend.shared.UseCase;
import com.red_escuela.backend.shared.domain.EmailAddress;
import com.red_escuela.backend.shared.domain.InvalidEmailAddressException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@UseCase
public class LoginService implements LoginUseCase {
    private final PersonRepository personRepository;
    private final LoginAttemptRepository loginAttemptRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthTokenIssuer authTokenIssuer;
    private final int lockoutMaxAttempts;
    private final long lockoutDurationMinutes;

    public LoginService(PersonRepository personRepository, LoginAttemptRepository loginAttemptRepository,
                         PasswordEncoder passwordEncoder, AuthTokenIssuer authTokenIssuer,
                         @Value("${auth.lockout.max-attempts}") int lockoutMaxAttempts,
                         @Value("${auth.lockout.duration-minutes}") long lockoutDurationMinutes) {
        this.personRepository = personRepository;
        this.loginAttemptRepository = loginAttemptRepository;
        this.passwordEncoder = passwordEncoder;
        this.authTokenIssuer = authTokenIssuer;
        this.lockoutMaxAttempts = lockoutMaxAttempts;
        this.lockoutDurationMinutes = lockoutDurationMinutes;
    }

    @Override
    @Transactional(noRollbackFor = InvalidCredentialsException.class)
    public AuthTokens login(LoginCommand command) {
        Optional<Person> personOpt = personRepository.findByUsername(command.username())
                .or(() -> findByEmailIfValid(command.username()))
                .or(() -> personRepository.findByDni(command.username()));

        if (personOpt.isEmpty()) {
            recordAttempt(command.username(), null, false, command.ipAddress());
            throw new InvalidCredentialsException();
        }

        Person person = personOpt.get();

        if (isLocked(person) || person.getPassword() == null
                || person.getStatus() != StatusType.ACTIVE) {
            recordAttempt(command.username(), person, false, command.ipAddress());
            throw new InvalidCredentialsException();
        }

        boolean matches = passwordEncoder.matches(command.password(), person.getPassword());
        recordAttempt(command.username(), person, matches, command.ipAddress());

        if (!matches) {
            registerFailedAttempt(person);
            throw new InvalidCredentialsException();
        }

        resetLockout(person);
        return authTokenIssuer.issueTokens(person);
    }

    private boolean isLocked(Person person) {
        return person.getLockedUntil() != null && person.getLockedUntil().isAfter(LocalDateTime.now());
    }

    private void registerFailedAttempt(Person person) {
        int attempts = person.getFailedLoginAttempts() + 1;
        if (attempts >= lockoutMaxAttempts) {
            person.setLockedUntil(LocalDateTime.now().plusMinutes(lockoutDurationMinutes));
            person.setFailedLoginAttempts(0);
        } else {
            person.setFailedLoginAttempts(attempts);
        }
        personRepository.save(person);
    }

    private void resetLockout(Person person) {
        person.setFailedLoginAttempts(0);
        person.setLockedUntil(null);
        personRepository.save(person);
    }

    private void recordAttempt(String attemptedUsername, Person person, boolean successful, String ipAddress) {
        LoginAttempt attempt = new LoginAttempt();
        attempt.setAttemptedUsername(attemptedUsername);
        attempt.setPerson(person);
        attempt.setSuccessful(successful);
        attempt.setIpAddress(ipAddress);
        attempt.setAttemptedAt(LocalDateTime.now());
        loginAttemptRepository.save(attempt);
    }

    private Optional<Person> findByEmailIfValid(String candidate) {
        try {
            return personRepository.findByEmail(EmailAddress.of(candidate));
        } catch (InvalidEmailAddressException e) {
            return Optional.empty();
        }
    }
}
