package com.red_escuela.backend.identity.specialist;

import com.red_escuela.backend.identity.dto.LegalAcceptanceEvidence;
import com.red_escuela.backend.identity.entity.LegalGuardianConfirmation;
import com.red_escuela.backend.identity.exception.InvalidGuardianConfirmationTokenException;
import com.red_escuela.backend.identity.repository.LegalGuardianConfirmationRepository;
import com.red_escuela.backend.identity.util.TokenHashUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

@Service
public class GuardianConfirmationConfirmer {

    private final LegalGuardianConfirmationRepository confirmationRepository;
    private final LegalAcceptanceRecorder legalAcceptanceRecorder;
    private final Clock clock;

    public GuardianConfirmationConfirmer(LegalGuardianConfirmationRepository confirmationRepository,
                                         LegalAcceptanceRecorder legalAcceptanceRecorder, Clock clock) {
        this.confirmationRepository = confirmationRepository;
        this.legalAcceptanceRecorder = legalAcceptanceRecorder;
        this.clock = clock;
    }

    @Transactional
    public void confirm(String token, LegalAcceptanceEvidence evidence) {
        String tokenHash = TokenHashUtil.sha256Hex(token.trim());
        if (confirmationRepository.claimByTokenHash(tokenHash, LocalDateTime.now(clock)) == 0) {
            throw new InvalidGuardianConfirmationTokenException();
        }
        LegalGuardianConfirmation confirmation = confirmationRepository.findByTokenHash(tokenHash)
                .orElseThrow(InvalidGuardianConfirmationTokenException::new);
        legalAcceptanceRecorder.acceptCurrentDocuments(confirmation.getPersonId(), evidence);
    }
}
