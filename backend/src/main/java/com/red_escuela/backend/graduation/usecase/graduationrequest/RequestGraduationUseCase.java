package com.red_escuela.backend.graduation.usecase.graduationrequest;

import com.red_escuela.backend.graduation.entity.GraduationRequest;

public interface RequestGraduationUseCase {
    GraduationRequest request(Long programEnrollmentId);
}
