package com.red_escuela.backend.enrollment.usecase.informativesession;

import com.red_escuela.backend.enrollment.dto.InformativeSessionResponse;
import com.red_escuela.backend.enrollment.service.RegisterForInformativeSessionTransaction;
import com.red_escuela.backend.identity.api.CurrentActorFinder;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class RegisterForInformativeSessionService implements RegisterForInformativeSessionUseCase {

    private final RegisterForInformativeSessionTransaction registerForInformativeSessionTransaction;
    private final CurrentActorFinder currentActorFinder;

    public RegisterForInformativeSessionService(RegisterForInformativeSessionTransaction registerForInformativeSessionTransaction,
                                                CurrentActorFinder currentActorFinder) {
        this.registerForInformativeSessionTransaction = registerForInformativeSessionTransaction;
        this.currentActorFinder = currentActorFinder;
    }

    @Override
    public InformativeSessionResponse register(Long sessionId) {
        return registerForInformativeSessionTransaction.register(sessionId, currentActorFinder.currentPersonId());
    }
}
