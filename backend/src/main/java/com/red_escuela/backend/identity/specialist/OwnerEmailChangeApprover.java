package com.red_escuela.backend.identity.specialist;

import com.red_escuela.backend.identity.entity.EmailVerificationToken;
import com.red_escuela.backend.identity.repository.EmailVerificationTokenRepository;
import com.red_escuela.backend.shared.exception.InvalidEmailVerificationTokenException;
import com.red_escuela.backend.shared.time.ApplicationTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OwnerEmailChangeApprover {

    private final EmailVerificationTokenRepository tokenRepository;

    public OwnerEmailChangeApprover(EmailVerificationTokenRepository tokenRepository) {
        this.tokenRepository = tokenRepository;
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
}
