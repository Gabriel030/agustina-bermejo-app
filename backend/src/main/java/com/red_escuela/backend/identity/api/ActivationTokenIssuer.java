package com.red_escuela.backend.identity.api;

import com.red_escuela.backend.identity.entity.ActivationToken;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.exception.PersonAlreadyActivatedException;
import com.red_escuela.backend.identity.exception.PersonNotEligibleForActivationException;
import com.red_escuela.backend.identity.exception.PersonNotFoundException;
import com.red_escuela.backend.identity.repository.ActivationTokenRepository;
import com.red_escuela.backend.identity.repository.PersonRepository;
import com.red_escuela.backend.identity.util.TokenHashUtil;
import com.red_escuela.backend.shared.domain.StatusType;
import com.red_escuela.backend.shared.time.ApplicationTime;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;

@Component
public class ActivationTokenIssuer {

    private static final String STUDENT_ROLE = "ALUMNO";

    private final PersonRepository personRepository;
    private final ActivationTokenRepository activationTokenRepository;
    private final long expirationDays;
    private final SecureRandom secureRandom = new SecureRandom();

    public ActivationTokenIssuer(PersonRepository personRepository, ActivationTokenRepository activationTokenRepository,
                                 @Value("${auth.activation-token-expiration-days:7}") long expirationDays) {
        this.personRepository = personRepository;
        this.activationTokenRepository = activationTokenRepository;
        this.expirationDays = expirationDays;
    }

    @Transactional
    public IssuedActivationToken issueActivationToken(Long personId) {
        Person person = personRepository.findByIdForUpdate(personId)
                .orElseThrow(() -> new PersonNotFoundException(personId));
        if (person.getPassword() != null) {
            throw new PersonAlreadyActivatedException(personId);
        }

        if (person.getStatus() != StatusType.ACTIVE
                || person.emailValue() == null || person.emailValue().isBlank()
                || person.getRoles().stream().noneMatch(role -> STUDENT_ROLE.equals(role.getName()))) {
            throw new PersonNotEligibleForActivationException(personId);
        }

        activationTokenRepository.deleteByPerson(person);
        activationTokenRepository.flush();

        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        String clearToken = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        LocalDateTime now = ApplicationTime.now();
        LocalDateTime expiresAt = now.plusDays(expirationDays);

        activationTokenRepository.save(ActivationToken.builder()
                .person(person)
                .email(person.emailValue())
                .tokenHash(TokenHashUtil.sha256Hex(clearToken))
                .expiresAt(expiresAt)
                .createdAt(now)
                .build());
        return new IssuedActivationToken(clearToken, expiresAt, person.emailValue());
    }
}
