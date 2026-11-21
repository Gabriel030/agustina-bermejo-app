package com.red_escuela.backend.identity.service;

import com.red_escuela.backend.identity.dto.AuthTokens;
import com.red_escuela.backend.shared.domain.StatusType;
import com.red_escuela.backend.identity.entity.LoginAttempt;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.exception.InvalidCredentialsException;
import com.red_escuela.backend.identity.specialist.DummyPasswordHash;
import com.red_escuela.backend.identity.repository.LoginAttemptRepository;
import com.red_escuela.backend.identity.repository.PersonRepository;
import com.red_escuela.backend.identity.specialist.AuthTokenIssuer;
import org.springframework.stereotype.Service;
import com.red_escuela.backend.shared.domain.EmailAddress;
import com.red_escuela.backend.shared.domain.DocumentType;
import com.red_escuela.backend.shared.domain.InvalidEmailAddressException;
import com.red_escuela.backend.shared.time.ApplicationTime;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class CredentialLoginTransaction {
    private final PersonRepository personRepository;
    private final LoginAttemptRepository loginAttemptRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthTokenIssuer authTokenIssuer;
    private final DummyPasswordHash dummyPasswordHash;
    private final int lockoutMaxAttempts;
    private final long lockoutDurationMinutes;

    public CredentialLoginTransaction(PersonRepository personRepository, LoginAttemptRepository loginAttemptRepository,
                         PasswordEncoder passwordEncoder, AuthTokenIssuer authTokenIssuer,
                         DummyPasswordHash dummyPasswordHash,
                         @Value("${auth.lockout.max-attempts}") int lockoutMaxAttempts,
                         @Value("${auth.lockout.duration-minutes}") long lockoutDurationMinutes) {
        this.personRepository = personRepository;
        this.loginAttemptRepository = loginAttemptRepository;
        this.passwordEncoder = passwordEncoder;
        this.authTokenIssuer = authTokenIssuer;
        this.dummyPasswordHash = dummyPasswordHash;
        this.lockoutMaxAttempts = lockoutMaxAttempts;
        this.lockoutDurationMinutes = lockoutDurationMinutes;
    }

    @Transactional(noRollbackFor = InvalidCredentialsException.class)
    public AuthTokens login(String username, String password, String ipAddress) {
        Optional<Person> personOpt = personRepository.findByUsername(username)
                .or(() -> findByEmailIfValid(username))
                .or(() -> personRepository.findByDocumentTypeAndDni(DocumentType.DNI, username));

        if (personOpt.isEmpty()) {
            passwordEncoder.matches(password, dummyPasswordHash.value());
            recordAttempt(username, null, false, ipAddress);
            throw new InvalidCredentialsException();
        }

        Person person = personOpt.get();

        if (isLocked(person) || person.getPassword() == null
                || person.getStatus() != StatusType.ACTIVE) {
            passwordEncoder.matches(password, dummyPasswordHash.value());
            recordAttempt(username, person, false, ipAddress);
            throw new InvalidCredentialsException();
        }

        boolean matches = passwordEncoder.matches(password, person.getPassword());
        recordAttempt(username, person, matches, ipAddress);

        if (!matches) {
            registerFailedAttempt(person);
            throw new InvalidCredentialsException();
        }

        resetLockout(person);
        return authTokenIssuer.issueTokens(person);
    }

    private boolean isLocked(Person person) {
        return person.getLockedUntil() != null && person.getLockedUntil().isAfter(ApplicationTime.now());
    }

    private void registerFailedAttempt(Person person) {
        int attempts = person.getFailedLoginAttempts() + 1;
        if (attempts >= lockoutMaxAttempts) {
            person.setLockedUntil(ApplicationTime.now().plusMinutes(lockoutDurationMinutes));
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
        attempt.setAttemptedAt(ApplicationTime.now());
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
