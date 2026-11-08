package com.red_escuela.backend.graduation.specialist;

import com.red_escuela.backend.billing.api.StudentDebtView;

public record GraduationDebtSummary(boolean mustCheckDebt, StudentDebtView debt) {
}
