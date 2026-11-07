package com.red_escuela.backend.graduation.usecase.request;

import com.red_escuela.backend.graduation.dto.GraduationDebtSummaryResponse;

public interface GetGraduationRequestDebtSummaryUseCase {
    GraduationDebtSummaryResponse get(Long graduationRequestId);
}
