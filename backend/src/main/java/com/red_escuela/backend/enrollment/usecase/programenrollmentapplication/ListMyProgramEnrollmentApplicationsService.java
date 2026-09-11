package com.red_escuela.backend.enrollment.usecase.programenrollmentapplication;

import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentApplication;
import com.red_escuela.backend.enrollment.repository.ProgramEnrollmentApplicationRepository;
import com.red_escuela.backend.shared.UseCase;

import java.util.List;

@UseCase
public class ListMyProgramEnrollmentApplicationsService implements ListMyProgramEnrollmentApplicationsUseCase {

    private final ProgramEnrollmentApplicationRepository applicationRepository;

    public ListMyProgramEnrollmentApplicationsService(ProgramEnrollmentApplicationRepository applicationRepository) {
        this.applicationRepository = applicationRepository;
    }

    @Override
    public List<ProgramEnrollmentApplication> list(Long studentId) {
        return applicationRepository.findByStudentIdOrderByRequestedAtDesc(studentId);
    }
}
