package com.red_escuela.backend.identity.specialist;

import com.red_escuela.backend.identity.entity.EmailVerificationToken;
import com.red_escuela.backend.shared.exception.InvalidEmailVerificationTokenException;
import com.red_escuela.backend.shared.time.ApplicationTime;
import org.springframework.stereotype.Service;

@Service
public class OwnerEmailChangeApprover {

    public void approve(EmailVerificationToken token) {
        if (!token.isAwaitingOwnerApproval() || token.getExpiresAt().isBefore(ApplicationTime.now())) {
            throw new InvalidEmailVerificationTokenException();
        }
        token.setAwaitingOwnerApproval(false);
        token.setOwnerApprovedAt(ApplicationTime.now());
    }
}
