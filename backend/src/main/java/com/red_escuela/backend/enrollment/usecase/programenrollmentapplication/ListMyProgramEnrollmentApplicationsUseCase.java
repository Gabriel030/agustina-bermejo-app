package com.red_escuela.backend.enrollment.usecase.programenrollmentapplication;

import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentApplication;

import java.util.List;

public interface ListMyProgramEnrollmentApplicationsUseCase {
    List<ProgramEnrollmentApplication> list(Long studentId);
}
