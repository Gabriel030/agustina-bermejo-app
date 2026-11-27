package com.red_escuela.backend.enrollment.usecase.programenrollment;

import com.red_escuela.backend.enrollment.dto.ProgramEnrollmentPlanChangeResponse;
import com.red_escuela.backend.enrollment.service.ChangeProgramEnrollmentPlanTransaction;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class ChangeProgramEnrollmentPlanService implements ChangeProgramEnrollmentPlanUseCase {

    private final ChangeProgramEnrollmentPlanTransaction changeProgramEnrollmentPlanTransaction;

    public ChangeProgramEnrollmentPlanService(ChangeProgramEnrollmentPlanTransaction changeProgramEnrollmentPlanTransaction) {
        this.changeProgramEnrollmentPlanTransaction = changeProgramEnrollmentPlanTransaction;
    }

    @Override
    public ProgramEnrollmentPlanChangeResponse change(ChangeProgramEnrollmentPlanCommand command) {
        return changeProgramEnrollmentPlanTransaction.change(command.programEnrollmentId(), command.request());
    }
}
