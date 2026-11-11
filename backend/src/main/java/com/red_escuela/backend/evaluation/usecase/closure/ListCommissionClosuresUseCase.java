package com.red_escuela.backend.evaluation.usecase.closure;

import com.red_escuela.backend.evaluation.dto.CommissionClosurePageResponse;

public interface ListCommissionClosuresUseCase {

    CommissionClosurePageResponse list(ListCommissionClosuresCommand command);
}
