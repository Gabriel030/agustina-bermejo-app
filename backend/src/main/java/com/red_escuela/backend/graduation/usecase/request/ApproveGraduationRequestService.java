package com.red_escuela.backend.graduation.usecase.request;

import com.red_escuela.backend.graduation.entity.GraduationRequest;
import com.red_escuela.backend.graduation.specialist.ApproveGraduationRequestTransaction;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class ApproveGraduationRequestService implements ApproveGraduationRequestUseCase {

    private final ApproveGraduationRequestTransaction approveGraduationRequestTransaction;

    public ApproveGraduationRequestService(ApproveGraduationRequestTransaction approveGraduationRequestTransaction) {
        this.approveGraduationRequestTransaction = approveGraduationRequestTransaction;
    }

    @Override
    public GraduationRequest approve(Long graduationRequestId) {
        return approveGraduationRequestTransaction.approve(graduationRequestId);
    }
}
