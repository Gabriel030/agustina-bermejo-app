package com.red_escuela.backend.identity.service;

import com.red_escuela.backend.identity.entity.ActivationToken;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.exception.ActivationTokenExpiredException;
import com.red_escuela.backend.identity.exception.ActivationTokenInvalidException;
import com.red_escuela.backend.identity.repository.ActivationTokenRepository;
import com.red_escuela.backend.identity.repository.PersonRepository;
import com.red_escuela.backend.identity.specialist.PasswordPolicyValidator;
import com.red_escuela.backend.identity.util.TokenHashUtil;
import com.red_escuela.backend.shared.time.ApplicationTime;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class AccountActivationTransaction {
    private final ActivationTokenRepository activationTokenRepository;
    private final PersonRepository personRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicyValidator passwordPolicyValidator;

    public AccountActivationTransaction(ActivationTokenRepository activationTokenRepository,
                                        PersonRepository personRepository, PasswordEncoder passwordEncoder,
                                        PasswordPolicyValidator passwordPolicyValidator) {
        this.activationTokenRepository = activationTokenRepository;
        this.personRepository = personRepository;
        this.passwordEncoder = passwordEncoder;
        this.passwordPolicyValidator = passwordPolicyValidator;
    }

    @Transactional
    public void activate(String clearToken, String newPassword) {
        passwordPolicyValidator.validate(newPassword);

        String hash = TokenHashUtil.sha256Hex(clearToken);
        LocalDateTime now = ApplicationTime.now();
        ActivationToken token = activationTokenRepository.findByTokenHash(hash)
                .filter(found -> found.getUsedAt() == null)
                .orElseThrow(ActivationTokenInvalidException::new);
        if (!token.getExpiresAt().isAfter(now)) {
            throw new ActivationTokenExpiredException();
        }
        Person person = token.getPerson();
        if (person.getPassword() != null) {
            throw new ActivationTokenInvalidException();
        }
        if (activationTokenRepository.markAsUsedIfRedeemable(hash, now) != 1) {
            throw new ActivationTokenInvalidException();
        }

        person.setPassword(passwordEncoder.encode(newPassword));
        person.setEmailVerified(true);
        person.setGrantSystemAccess(true);
        personRepository.save(person);
    }
}
