package com.red_escuela.backend.graduation.usecase.graduationrequest;

import com.red_escuela.backend.graduation.entity.GraduationRequest;

import java.util.List;

public interface ListGraduationRequestsByProgramEnrollmentUseCase {
    List<GraduationRequest> list(Long programEnrollmentId);
}
