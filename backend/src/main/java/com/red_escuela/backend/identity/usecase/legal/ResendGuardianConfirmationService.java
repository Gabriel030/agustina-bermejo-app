package com.red_escuela.backend.identity.usecase.legal;

import com.red_escuela.backend.identity.specialist.GuardianConfirmationResender;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class ResendGuardianConfirmationService implements ResendGuardianConfirmationUseCase {

    private final GuardianConfirmationResender guardianConfirmationResender;

    public ResendGuardianConfirmationService(GuardianConfirmationResender guardianConfirmationResender) {
        this.guardianConfirmationResender = guardianConfirmationResender;
    }

    @Override
    public void resendGuardianConfirmation() {
        guardianConfirmationResender.resendForCurrentPerson();
    }
}
