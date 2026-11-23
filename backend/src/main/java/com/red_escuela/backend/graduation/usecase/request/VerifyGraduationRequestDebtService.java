package com.red_escuela.backend.graduation.usecase.request;

import com.red_escuela.backend.graduation.entity.GraduationRequest;
import com.red_escuela.backend.graduation.specialist.VerifyGraduationRequestDebtTransaction;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class VerifyGraduationRequestDebtService implements VerifyGraduationRequestDebtUseCase {

    private final VerifyGraduationRequestDebtTransaction verifyGraduationRequestDebtTransaction;

    public VerifyGraduationRequestDebtService(VerifyGraduationRequestDebtTransaction verifyGraduationRequestDebtTransaction) {
        this.verifyGraduationRequestDebtTransaction = verifyGraduationRequestDebtTransaction;
    }

    @Override
    public GraduationRequest verify(Long graduationRequestId) {
        return verifyGraduationRequestDebtTransaction.verify(graduationRequestId);
    }
}
