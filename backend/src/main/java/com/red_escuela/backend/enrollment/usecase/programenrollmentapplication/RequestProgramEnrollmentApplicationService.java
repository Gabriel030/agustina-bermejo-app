package com.red_escuela.backend.enrollment.usecase.programenrollmentapplication;

import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentApplication;
import com.red_escuela.backend.enrollment.service.ProgramApplicationRegistrar;
import com.red_escuela.backend.shared.UseCase;
import org.springframework.transaction.annotation.Transactional;

@UseCase
public class RequestProgramEnrollmentApplicationService implements RequestProgramEnrollmentApplicationUseCase {

    private final ProgramApplicationRegistrar applicationRegistrar;

    public RequestProgramEnrollmentApplicationService(ProgramApplicationRegistrar applicationRegistrar) {
        this.applicationRegistrar = applicationRegistrar;
    }

    @Override
    @Transactional
    public ProgramEnrollmentApplication request(RequestProgramEnrollmentApplicationCommand command) {
        return applicationRegistrar.register(command.student(), command.programId(), command.cohortId());
    }
}
