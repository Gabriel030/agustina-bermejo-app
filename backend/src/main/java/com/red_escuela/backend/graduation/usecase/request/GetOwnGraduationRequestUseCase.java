package com.red_escuela.backend.graduation.usecase.request;

import com.red_escuela.backend.graduation.dto.OwnGraduationRequestResponse;

public interface GetOwnGraduationRequestUseCase {

    OwnGraduationRequestResponse get(Long programEnrollmentId);
}
