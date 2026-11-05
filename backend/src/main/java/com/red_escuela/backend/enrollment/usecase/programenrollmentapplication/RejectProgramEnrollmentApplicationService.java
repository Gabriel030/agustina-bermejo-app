package com.red_escuela.backend.enrollment.usecase.programenrollmentapplication;

import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentApplication;
import com.red_escuela.backend.enrollment.service.ProgramEnrollmentApplicationRejection;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class RejectProgramEnrollmentApplicationService implements RejectProgramEnrollmentApplicationUseCase {

    private final ProgramEnrollmentApplicationRejection applicationRejection;

    public RejectProgramEnrollmentApplicationService(ProgramEnrollmentApplicationRejection applicationRejection) {
        this.applicationRejection = applicationRejection;
    }

    @Override
    public ProgramEnrollmentApplication reject(RejectProgramEnrollmentApplicationCommand command) {
        return applicationRejection.reject(command.id(), command.decidedBy(), command.reason());
    }
}
