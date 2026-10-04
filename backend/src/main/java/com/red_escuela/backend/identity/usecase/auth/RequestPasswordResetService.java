package com.red_escuela.backend.identity.usecase.auth;

import com.red_escuela.backend.shared.domain.EmailAddress;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.entity.PasswordResetToken;
import com.red_escuela.backend.identity.repository.PasswordResetTokenRepository;
import com.red_escuela.backend.identity.repository.PersonRepository;
import com.red_escuela.backend.identity.service.PasswordResetCodeIssuedEvent;
import org.springframework.context.ApplicationEventPublisher;
import com.red_escuela.backend.shared.UseCase;
import com.red_escuela.backend.identity.util.TokenHashUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Optional;

@UseCase
public class RequestPasswordResetService implements RequestPasswordResetUseCase {
    private static final Logger log = LoggerFactory.getLogger(RequestPasswordResetService.class);

    private final PersonRepository personRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final long passwordResetCodeExpirationMinutes;
    private final SecureRandom secureRandom = new SecureRandom();

    public RequestPasswordResetService(PersonRepository personRepository, PasswordResetTokenRepository passwordResetTokenRepository,
                                        ApplicationEventPublisher eventPublisher,
                                        @Value("${auth.password-reset-code-expiration-minutes}") long passwordResetCodeExpirationMinutes) {
        this.personRepository = personRepository;
        this.passwordResetTokenRepository = passwordResetTokenRepository;
        this.eventPublisher = eventPublisher;
        this.passwordResetCodeExpirationMinutes = passwordResetCodeExpirationMinutes;
    }

    @Override
    @Transactional
    public void requestReset(String email) {
        Optional<Person> personOpt = personRepository.findByEmail(EmailAddress.of(email));
        if (personOpt.isEmpty()) {
            return;
        }
        Person person = personOpt.get();

        String code = generateResetCode();

        PasswordResetToken resetToken = new PasswordResetToken();
        resetToken.setPerson(person);
        resetToken.setTokenHash(TokenHashUtil.sha256Hex(code));
        resetToken.setExpiresAt(LocalDateTime.now().plusMinutes(passwordResetCodeExpirationMinutes));
        resetToken.setUsed(false);
        resetToken.setAttempts(0);
        resetToken.setCreatedAt(LocalDateTime.now());
        passwordResetTokenRepository.save(resetToken);

        eventPublisher.publishEvent(new PasswordResetCodeIssuedEvent(person, code));
        log.info("Código de recuperación de password generado para person {}", person.getId());
    }

    private String generateResetCode() {
        return String.format("%06d", secureRandom.nextInt(1_000_000));
    }
}
