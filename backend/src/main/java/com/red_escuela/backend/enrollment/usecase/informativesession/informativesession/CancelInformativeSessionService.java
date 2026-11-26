package com.red_escuela.backend.enrollment.usecase.informativesession;

import com.red_escuela.backend.enrollment.dto.InformativeSessionResponse;
import com.red_escuela.backend.enrollment.service.CancelInformativeSessionTransaction;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class CancelInformativeSessionService implements CancelInformativeSessionUseCase {

    private final CancelInformativeSessionTransaction cancelInformativeSessionTransaction;

    public CancelInformativeSessionService(CancelInformativeSessionTransaction cancelInformativeSessionTransaction) {
        this.cancelInformativeSessionTransaction = cancelInformativeSessionTransaction;
    }

    @Override
    public InformativeSessionResponse cancel(Long sessionId) {
        return cancelInformativeSessionTransaction.cancel(sessionId);
    }
}
