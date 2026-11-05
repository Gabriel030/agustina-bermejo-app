package com.red_escuela.backend.graduation.usecase.graduationrequest;

import com.red_escuela.backend.graduation.dto.OwnGraduationRequestResponse;

public interface GetOwnGraduationRequestUseCase {

    OwnGraduationRequestResponse get(Long programEnrollmentId);
}
