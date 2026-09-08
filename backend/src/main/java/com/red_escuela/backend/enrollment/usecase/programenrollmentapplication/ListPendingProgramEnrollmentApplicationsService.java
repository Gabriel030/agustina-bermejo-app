package com.red_escuela.backend.enrollment.usecase.programenrollmentapplication;

import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentApplication;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentApplicationStatus;
import com.red_escuela.backend.enrollment.repository.ProgramEnrollmentApplicationRepository;
import com.red_escuela.backend.shared.UseCase;

import java.util.List;

@UseCase
public class ListPendingProgramEnrollmentApplicationsService implements ListPendingProgramEnrollmentApplicationsUseCase {

    private final ProgramEnrollmentApplicationRepository applicationRepository;

    public ListPendingProgramEnrollmentApplicationsService(ProgramEnrollmentApplicationRepository applicationRepository) {
        this.applicationRepository = applicationRepository;
    }

    @Override
    public List<ProgramEnrollmentApplication> list() {
        return applicationRepository.findByStatusOrderByRequestedAtAsc(ProgramEnrollmentApplicationStatus.PENDING);
    }
}
