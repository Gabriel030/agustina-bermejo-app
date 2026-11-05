package com.red_escuela.backend.graduation.usecase.graduationrequest;

import com.red_escuela.backend.graduation.service.GraduationDebtSummary;
import com.red_escuela.backend.graduation.service.GraduationDebtSummaryReader;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class GetGraduationRequestDebtSummaryService implements GetGraduationRequestDebtSummaryUseCase {

    private final GraduationDebtSummaryReader graduationDebtSummaryReader;

    public GetGraduationRequestDebtSummaryService(GraduationDebtSummaryReader graduationDebtSummaryReader) {
        this.graduationDebtSummaryReader = graduationDebtSummaryReader;
    }

    @Override
    public GraduationDebtSummary get(Long graduationRequestId) {
        return graduationDebtSummaryReader.summaryOf(graduationRequestId);
    }
}
