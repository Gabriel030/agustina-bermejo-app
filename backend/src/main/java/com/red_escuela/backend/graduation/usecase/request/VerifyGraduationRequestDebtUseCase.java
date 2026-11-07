package com.red_escuela.backend.graduation.usecase.request;

import com.red_escuela.backend.graduation.entity.GraduationRequest;

public interface VerifyGraduationRequestDebtUseCase {
    GraduationRequest verify(Long graduationRequestId);
}
