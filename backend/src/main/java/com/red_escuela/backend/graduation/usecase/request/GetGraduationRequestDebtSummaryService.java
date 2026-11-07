package com.red_escuela.backend.graduation.usecase.request;

import com.red_escuela.backend.graduation.dto.GraduationDebtSummaryResponse;
import com.red_escuela.backend.graduation.specialist.GraduationDebtChecker;
import com.red_escuela.backend.graduation.specialist.GraduationDebtSummary;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class GetGraduationRequestDebtSummaryService implements GetGraduationRequestDebtSummaryUseCase {

    private final GraduationDebtChecker graduationDebtChecker;

    public GetGraduationRequestDebtSummaryService(GraduationDebtChecker graduationDebtChecker) {
        this.graduationDebtChecker = graduationDebtChecker;
    }

    @Override
    public GraduationDebtSummaryResponse get(Long graduationRequestId) {
        GraduationDebtSummary summary = graduationDebtChecker.summaryOf(graduationRequestId);
        return GraduationDebtSummaryResponse.of(summary.mustCheckDebt(), summary.debt());
    }
}
