package com.red_escuela.backend.identity.usecase.emailverification;

import com.red_escuela.backend.identity.entity.EmailVerificationToken;
import com.red_escuela.backend.identity.exception.InvalidEmailVerificationTokenException;
import com.red_escuela.backend.identity.repository.EmailVerificationTokenRepository;
import com.red_escuela.backend.shared.UseCase;
import com.red_escuela.backend.util.TokenHashUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@UseCase
public class VerifyEmailService implements VerifyEmailUseCase {

    private static final Logger log = LoggerFactory.getLogger(VerifyEmailService.class);

    private final EmailVerificationTokenRepository tokenRepository;

    public VerifyEmailService(EmailVerificationTokenRepository tokenRepository) {
        this.tokenRepository = tokenRepository;
    }

    @Override
    @Transactional
    public void verify(String clearToken) {
        String hash = TokenHashUtil.sha256Hex(clearToken);
        EmailVerificationToken token = tokenRepository.findByTokenHash(hash)
                .orElseThrow(() -> {
                    log.warn("Token de verificación de email no encontrado");
                    return new InvalidEmailVerificationTokenException();
                });

        if (token.getExpiresAt().isBefore(LocalDateTime.now())) {
            log.warn("Token de verificación de email expirado para person {}", token.getPerson().getId());
            throw new InvalidEmailVerificationTokenException();
        }

        token.getPerson().setEmailVerified(true);
        tokenRepository.delete(token);
        log.info("Email verificado para person {}", token.getPerson().getId());
    }
}
