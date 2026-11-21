package com.red_escuela.backend.identity.service;

import com.red_escuela.backend.identity.entity.PasswordResetToken;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.exception.InvalidPasswordResetTokenException;
import com.red_escuela.backend.identity.repository.PasswordResetTokenRepository;
import com.red_escuela.backend.identity.repository.PersonRepository;
import com.red_escuela.backend.identity.specialist.IdentityAuditRecorder;
import com.red_escuela.backend.identity.specialist.PasswordPolicyValidator;
import com.red_escuela.backend.identity.specialist.PublicPasswordResetBlockPolicy;
import com.red_escuela.backend.identity.specialist.SessionRevoker;
import com.red_escuela.backend.identity.util.TokenHashUtil;
import com.red_escuela.backend.shared.time.ApplicationTime;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;


@Service
public class PasswordResetTransaction {
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final PersonRepository personRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicyValidator passwordPolicyValidator;
    private final SessionRevoker sessionRevoker;
    private final IdentityAuditRecorder identityAuditRecorder;
    private final PublicPasswordResetBlockPolicy publicPasswordResetBlockPolicy;

    public PasswordResetTransaction(PasswordResetTokenRepository passwordResetTokenRepository, PersonRepository personRepository,
                                 PasswordEncoder passwordEncoder, PasswordPolicyValidator passwordPolicyValidator,
                                 SessionRevoker sessionRevoker, IdentityAuditRecorder identityAuditRecorder,
                                 PublicPasswordResetBlockPolicy publicPasswordResetBlockPolicy) {
        this.passwordResetTokenRepository = passwordResetTokenRepository;
        this.personRepository = personRepository;
        this.passwordEncoder = passwordEncoder;
        this.passwordPolicyValidator = passwordPolicyValidator;
        this.sessionRevoker = sessionRevoker;
        this.identityAuditRecorder = identityAuditRecorder;
        this.publicPasswordResetBlockPolicy = publicPasswordResetBlockPolicy;
    }

    @Transactional
    public void reset(String exchangeToken, String newPassword) {
        passwordPolicyValidator.validate(newPassword);

        String hash = TokenHashUtil.sha256Hex(exchangeToken);
        LocalDateTime now = ApplicationTime.now();
        PasswordResetToken resetToken = passwordResetTokenRepository
                .findByExchangeTokenHashAndExchangeExpiresAtAfter(hash, now)
                .filter(found -> passwordResetTokenRepository.consumeExchangeToken(hash, now) == 1)
                .orElseThrow(InvalidPasswordResetTokenException::new);

        Person person = resetToken.getPerson();
        publicPasswordResetBlockPolicy.requireNotBlocked(person);
        person.setPassword(passwordEncoder.encode(newPassword));
        personRepository.save(person);
        sessionRevoker.revokeAllSessions(person);

        resetToken.setExchangeTokenHash(null);
        passwordResetTokenRepository.save(resetToken);
        identityAuditRecorder.recordPasswordResetCompleted(person);
    }
}
