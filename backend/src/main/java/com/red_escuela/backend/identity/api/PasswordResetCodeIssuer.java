package com.red_escuela.backend.identity.api;

import com.red_escuela.backend.shared.audit.AuditAction;
import com.red_escuela.backend.shared.audit.AuditEntityType;
import com.red_escuela.backend.shared.audit.AuditRecord;
import com.red_escuela.backend.shared.audit.service.AuditTrail;
import com.red_escuela.backend.identity.entity.PasswordResetToken;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.repository.PasswordResetTokenRepository;
import com.red_escuela.backend.identity.repository.PersonRepository;
import com.red_escuela.backend.identity.events.PasswordResetCodeIssuedEvent;
import com.red_escuela.backend.identity.util.TokenHashUtil;
import com.red_escuela.backend.shared.domain.EmailAddress;
import com.red_escuela.backend.shared.time.ApplicationTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.Optional;

@Component
public class PasswordResetCodeIssuer {
    private static final Logger log = LoggerFactory.getLogger(PasswordResetCodeIssuer.class);

    private final PersonRepository personRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final long passwordResetCodeExpirationMinutes;
    private final AuditTrail auditTrail;
    private final SecureRandom secureRandom = new SecureRandom();

    public PasswordResetCodeIssuer(PersonRepository personRepository, PasswordResetTokenRepository passwordResetTokenRepository,
                                   ApplicationEventPublisher eventPublisher,
                                   AuditTrail auditTrail,
                                   @Value("${auth.password-reset-code-expiration-minutes}") long passwordResetCodeExpirationMinutes) {
        this.personRepository = personRepository;
        this.passwordResetTokenRepository = passwordResetTokenRepository;
        this.eventPublisher = eventPublisher;
        this.passwordResetCodeExpirationMinutes = passwordResetCodeExpirationMinutes;
        this.auditTrail = auditTrail;
    }

    @Transactional
    public void issuePasswordResetCode(String email) {
        Optional<Person> personOpt = personRepository.findByEmail(EmailAddress.of(email));
        if (personOpt.isEmpty()) {
            return;
        }
        Person person = personOpt.get();

        String code = generateResetCode();

        PasswordResetToken resetToken = new PasswordResetToken();
        resetToken.setPerson(person);
        resetToken.setTokenHash(TokenHashUtil.sha256Hex(code));
        resetToken.setExpiresAt(ApplicationTime.now().plusMinutes(passwordResetCodeExpirationMinutes));
        resetToken.setUsed(false);
        resetToken.setAttempts(0);
        resetToken.setCreatedAt(ApplicationTime.now());
        passwordResetTokenRepository.save(resetToken);

        eventPublisher.publishEvent(PasswordResetCodeIssuedEvent.issuedFor(person.getId(), code));
        auditTrail.record(new AuditRecord(AuditAction.PASSWORD_RESET_REQUESTED, AuditEntityType.PERSON, person.getId(),
                null, null, null));
        log.info("Código de recuperación de password generado para person {}", person.getId());
    }

    private String generateResetCode() {
        return String.format("%06d", secureRandom.nextInt(1_000_000));
    }
}
