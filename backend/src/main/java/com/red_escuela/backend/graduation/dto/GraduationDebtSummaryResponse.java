package com.red_escuela.backend.graduation.dto;

import com.red_escuela.backend.billing.api.StudentDebtInstallmentView;
import com.red_escuela.backend.graduation.service.GraduationDebtSummary;
import com.red_escuela.backend.shared.domain.Money;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

public record GraduationDebtSummaryResponse(boolean mustCheckDebt, Money totalDue, List<InstallmentResponse> installments) {

    public record InstallmentResponse(YearMonth period, LocalDate dueDate, Money amount, String status) {
        static InstallmentResponse from(StudentDebtInstallmentView view) {
            return new InstallmentResponse(view.period(), view.dueDate(), view.amount(), view.status());
        }
    }

    public static GraduationDebtSummaryResponse from(GraduationDebtSummary summary) {
        return new GraduationDebtSummaryResponse(summary.mustCheckDebt(), summary.debt().totalDue(),
                summary.debt().installments().stream().map(InstallmentResponse::from).toList());
    }
}
