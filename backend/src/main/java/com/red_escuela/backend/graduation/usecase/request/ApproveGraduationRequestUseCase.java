package com.red_escuela.backend.graduation.usecase.request;

import com.red_escuela.backend.graduation.entity.GraduationRequest;

public interface ApproveGraduationRequestUseCase {
    GraduationRequest approve(Long graduationRequestId);
}
