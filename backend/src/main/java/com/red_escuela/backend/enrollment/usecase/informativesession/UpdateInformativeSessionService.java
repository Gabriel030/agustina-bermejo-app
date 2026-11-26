package com.red_escuela.backend.enrollment.usecase.informativesession;

import com.red_escuela.backend.enrollment.dto.InformativeSessionResponse;
import com.red_escuela.backend.enrollment.service.UpdateInformativeSessionTransaction;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class UpdateInformativeSessionService implements UpdateInformativeSessionUseCase {

    private final UpdateInformativeSessionTransaction updateInformativeSessionTransaction;

    public UpdateInformativeSessionService(UpdateInformativeSessionTransaction updateInformativeSessionTransaction) {
        this.updateInformativeSessionTransaction = updateInformativeSessionTransaction;
    }

    @Override
    public InformativeSessionResponse update(UpdateInformativeSessionCommand command) {
        return updateInformativeSessionTransaction.update(command.sessionId(), command.request());
    }
}
