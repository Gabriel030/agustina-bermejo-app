package com.red_escuela.backend.identity.usecase.auth;

import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.entity.PasswordResetToken;
import com.red_escuela.backend.identity.repository.PasswordResetTokenRepository;
import com.red_escuela.backend.identity.repository.PersonRepository;
import com.red_escuela.backend.service.EmailService;
import com.red_escuela.backend.shared.UseCase;
import com.red_escuela.backend.util.TokenHashUtil;
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
    private final EmailService emailService;
    private final long passwordResetCodeExpirationMinutes;
    private final SecureRandom secureRandom = new SecureRandom();

    public RequestPasswordResetService(PersonRepository personRepository, PasswordResetTokenRepository passwordResetTokenRepository,
                                        EmailService emailService,
                                        @Value("${auth.password-reset-code-expiration-minutes}") long passwordResetCodeExpirationMinutes) {
        this.personRepository = personRepository;
        this.passwordResetTokenRepository = passwordResetTokenRepository;
        this.emailService = emailService;
        this.passwordResetCodeExpirationMinutes = passwordResetCodeExpirationMinutes;
    }

    @Override
    @Transactional
    public void requestReset(String email) {
        // Universal: cualquier Person con este email recibe el código — no hay ningún caso en
        // el que este flujo no haga nada salvo que el email directamente no exista (anti-enumeración).
        Optional<Person> personOpt = personRepository.findByEmail(email);
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

        emailService.sendPasswordResetCode(person, code);
        log.info("Código de recuperación de password generado para person {}", person.getId());
    }

    private String generateResetCode() {
        return String.format("%06d", secureRandom.nextInt(1_000_000));
    }
}
