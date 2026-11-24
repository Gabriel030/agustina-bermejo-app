package com.red_escuela.backend.graduation.usecase.request;

import com.red_escuela.backend.graduation.entity.GraduationRequest;
import com.red_escuela.backend.graduation.specialist.RejectGraduationRequestTransaction;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class RejectGraduationRequestService implements RejectGraduationRequestUseCase {

    private final RejectGraduationRequestTransaction rejectGraduationRequestTransaction;

    public RejectGraduationRequestService(RejectGraduationRequestTransaction rejectGraduationRequestTransaction) {
        this.rejectGraduationRequestTransaction = rejectGraduationRequestTransaction;
    }

    @Override
    public GraduationRequest reject(RejectGraduationRequestCommand command) {
        return rejectGraduationRequestTransaction.reject(command.graduationRequestId(), command.motivo());
    }
}
