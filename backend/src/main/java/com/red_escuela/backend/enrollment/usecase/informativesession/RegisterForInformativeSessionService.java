package com.red_escuela.backend.enrollment.usecase.informativesession;

import com.red_escuela.backend.enrollment.dto.InformativeSessionResponse;
import com.red_escuela.backend.enrollment.service.RegisterForInformativeSessionTransaction;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class RegisterForInformativeSessionService implements RegisterForInformativeSessionUseCase {

    private final RegisterForInformativeSessionTransaction registerForInformativeSessionTransaction;

    public RegisterForInformativeSessionService(RegisterForInformativeSessionTransaction registerForInformativeSessionTransaction) {
        this.registerForInformativeSessionTransaction = registerForInformativeSessionTransaction;
    }

    @Override
    public InformativeSessionResponse register(Long sessionId) {
        return registerForInformativeSessionTransaction.register(sessionId);
    }
}
