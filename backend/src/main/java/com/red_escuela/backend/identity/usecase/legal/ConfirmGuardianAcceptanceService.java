package com.red_escuela.backend.identity.usecase.legal;

import com.red_escuela.backend.identity.dto.LegalAcceptanceEvidence;
import com.red_escuela.backend.identity.specialist.GuardianConfirmationConfirmer;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class ConfirmGuardianAcceptanceService implements ConfirmGuardianAcceptanceUseCase {

    private final GuardianConfirmationConfirmer guardianConfirmationConfirmer;

    public ConfirmGuardianAcceptanceService(GuardianConfirmationConfirmer guardianConfirmationConfirmer) {
        this.guardianConfirmationConfirmer = guardianConfirmationConfirmer;
    }

    @Override
    public void confirmGuardianAcceptance(String token, LegalAcceptanceEvidence evidence) {
        guardianConfirmationConfirmer.confirm(token, evidence);
    }
}
