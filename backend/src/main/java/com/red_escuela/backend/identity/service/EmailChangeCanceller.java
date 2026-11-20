package com.red_escuela.backend.identity.service;

import com.red_escuela.backend.identity.entity.EmailVerificationToken;
import com.red_escuela.backend.identity.repository.EmailVerificationTokenRepository;
import com.red_escuela.backend.identity.util.TokenHashUtil;
import com.red_escuela.backend.shared.exception.InvalidEmailVerificationTokenException;
import com.red_escuela.backend.shared.time.ApplicationTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EmailChangeCanceller {

    private static final Logger log = LoggerFactory.getLogger(EmailChangeCanceller.class);

    private final EmailVerificationTokenRepository tokenRepository;

    public EmailChangeCanceller(EmailVerificationTokenRepository tokenRepository) {
        this.tokenRepository = tokenRepository;
    }

    @Transactional
    public void cancelByClearToken(String clearToken) {
        EmailVerificationToken pending = tokenRepository.findByCancelTokenHash(TokenHashUtil.sha256Hex(clearToken))
                .filter(candidate -> !candidate.getExpiresAt().isBefore(ApplicationTime.now()))
                .orElseThrow(InvalidEmailVerificationTokenException::new);
        tokenRepository.delete(pending);
        log.info("Cambio de email cancelado desde el email anterior para person {}", pending.getPerson().getId());
    }
}
