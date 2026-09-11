package com.red_escuela.backend.enrollment.usecase.programenrollmentapplication;

import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentApplication;
import com.red_escuela.backend.enrollment.service.ProgramEnrollmentApplicationFinder;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class GetProgramEnrollmentApplicationService implements GetProgramEnrollmentApplicationUseCase {

    private final ProgramEnrollmentApplicationFinder applicationFinder;

    public GetProgramEnrollmentApplicationService(ProgramEnrollmentApplicationFinder applicationFinder) {
        this.applicationFinder = applicationFinder;
    }

    @Override
    public ProgramEnrollmentApplication get(Long id) {
        return applicationFinder.findOrThrow(id);
    }
}
