package com.red_escuela.backend.enrollment.usecase.programenrollmentapplication;

import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentApplication;
import com.red_escuela.backend.enrollment.service.ApproveProgramEnrollmentApplicationTransaction;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class ApproveProgramEnrollmentApplicationService implements ApproveProgramEnrollmentApplicationUseCase {

    private final ApproveProgramEnrollmentApplicationTransaction approveTransaction;

    public ApproveProgramEnrollmentApplicationService(ApproveProgramEnrollmentApplicationTransaction approveTransaction) {
        this.approveTransaction = approveTransaction;
    }

    @Override
    public ProgramEnrollmentApplication approve(ApproveProgramEnrollmentApplicationCommand command) {
        return approveTransaction.approve(command.id(), command.idempotencyKey(), command.decidedBy());
    }
}
