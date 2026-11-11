package com.red_escuela.backend.evaluation.usecase.closure;

import com.red_escuela.backend.evaluation.domain.CommissionClosureListFilter;

public record ListCommissionClosuresCommand(CommissionClosureListFilter filter, int page, int size) {
}
