package com.red_escuela.backend.evaluation.domain;

import com.red_escuela.backend.evaluation.entity.ClosureStatus;

public record CommissionClosureListFilter(ClosureStatus status, Long termId, String query) {
}
