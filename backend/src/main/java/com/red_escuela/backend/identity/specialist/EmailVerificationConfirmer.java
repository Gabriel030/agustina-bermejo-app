package com.red_escuela.backend.identity.specialist;

import com.red_escuela.backend.identity.entity.EmailVerificationToken;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.repository.EmailVerificationTokenRepository;
import com.red_escuela.backend.shared.exception.InvalidEmailVerificationTokenException;
import com.red_escuela.backend.shared.time.ApplicationTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class EmailVerificationConfirmer {

    private static final Logger log = LoggerFactory.getLogger(EmailVerificationConfirmer.class);

    private final EmailVerificationTokenRepository tokenRepository;
    private final PendingEmailChangeConfirmer pendingEmailChangeConfirmer;

    public EmailVerificationConfirmer(EmailVerificationTokenRepository tokenRepository,
                                         PendingEmailChangeConfirmer pendingEmailChangeConfirmer) {
        this.tokenRepository = tokenRepository;
        this.pendingEmailChangeConfirmer = pendingEmailChangeConfirmer;
    }

    @Transactional
    public void confirmByTokenHash(String tokenHash) {
        EmailVerificationToken token = tokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> {
                    log.warn("Token de verificación de email no encontrado");
                    return new InvalidEmailVerificationTokenException();
                });

        if (token.getExpiresAt().isBefore(ApplicationTime.now())) {
            log.warn("Token de verificación de email expirado para person {}", token.getPerson().getId());
            throw new InvalidEmailVerificationTokenException();
        }

        Person person = token.getPerson();
        pendingEmailChangeConfirmer.confirmIfPending(token);
        person.setEmailVerified(true);
        tokenRepository.delete(token);
        log.info("Email verificado para person {}", token.getPerson().getId());
    }
}
