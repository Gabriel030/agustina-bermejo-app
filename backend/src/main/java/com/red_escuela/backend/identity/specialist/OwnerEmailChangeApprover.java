package com.red_escuela.backend.identity.specialist;

import com.red_escuela.backend.identity.entity.EmailVerificationToken;
import com.red_escuela.backend.identity.repository.EmailVerificationTokenRepository;
import com.red_escuela.backend.identity.util.TokenHashUtil;
import com.red_escuela.backend.shared.exception.InvalidEmailVerificationTokenException;
import com.red_escuela.backend.shared.time.ApplicationTime;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.Base64;

@Service
public class OwnerEmailChangeApprover {

    private final EmailVerificationTokenRepository tokenRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final String frontendBaseUrl;
    private final SecureRandom secureRandom = new SecureRandom();

    public OwnerEmailChangeApprover(EmailVerificationTokenRepository tokenRepository, ApplicationEventPublisher eventPublisher,
                                    @Value("${app.frontend.base-url}") String frontendBaseUrl) {
        this.tokenRepository = tokenRepository;
        this.eventPublisher = eventPublisher;
        this.frontendBaseUrl = frontendBaseUrl;
    }

    @Transactional
    public void approve(EmailVerificationToken token) {
        if (!token.isAwaitingOwnerApproval() || token.getExpiresAt().isBefore(ApplicationTime.now())) {
            throw new InvalidEmailVerificationTokenException();
        }
        token.setAwaitingOwnerApproval(false);
        token.setOwnerApprovedAt(ApplicationTime.now());
        tokenRepository.save(token);
    }

    @Transactional
    public void approveAndSendVerification(EmailVerificationToken token) {
        approve(token);
        String clearToken = newClearToken();
        token.setTokenHash(TokenHashUtil.sha256Hex(clearToken));
        tokenRepository.save(token);
        eventPublisher.publishEvent(EmailChangeRequestedEvent.requestedFor(token.getPerson().getId(), null, token.getNewEmail(),
                frontendBaseUrl + "/verificar-email?token=" + clearToken, null));
    }

    private String newClearToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
