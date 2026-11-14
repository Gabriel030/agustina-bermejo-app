package com.red_escuela.backend.identity.usecase.legal;

import com.red_escuela.backend.identity.dto.LegalAcceptanceEvidence;

public interface ConfirmGuardianAcceptanceUseCase {
    void confirmGuardianAcceptance(String token, LegalAcceptanceEvidence evidence);
}
