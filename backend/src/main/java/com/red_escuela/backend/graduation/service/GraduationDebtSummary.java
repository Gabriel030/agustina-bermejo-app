package com.red_escuela.backend.graduation.service;

import com.red_escuela.backend.billing.api.StudentDebtView;

public record GraduationDebtSummary(boolean mustCheckDebt, StudentDebtView debt) {
}
