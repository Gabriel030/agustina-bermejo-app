package com.red_escuela.backend.graduation.usecase.graduationrequest;

import com.red_escuela.backend.graduation.service.GraduationDebtSummary;

public interface GetGraduationRequestDebtSummaryUseCase {
    GraduationDebtSummary get(Long graduationRequestId);
}
