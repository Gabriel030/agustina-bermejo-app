package com.red_escuela.backend.enrollment.usecase.informativesession;

import com.red_escuela.backend.enrollment.dto.InformativeSessionResponse;
import com.red_escuela.backend.enrollment.dto.SaveInformativeSessionRequest;
import com.red_escuela.backend.enrollment.service.CreateInformativeSessionTransaction;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class CreateInformativeSessionService implements CreateInformativeSessionUseCase {

    private final CreateInformativeSessionTransaction createInformativeSessionTransaction;

    public CreateInformativeSessionService(CreateInformativeSessionTransaction createInformativeSessionTransaction) {
        this.createInformativeSessionTransaction = createInformativeSessionTransaction;
    }

    @Override
    public InformativeSessionResponse create(SaveInformativeSessionRequest request) {
        return createInformativeSessionTransaction.create(request);
    }
}
