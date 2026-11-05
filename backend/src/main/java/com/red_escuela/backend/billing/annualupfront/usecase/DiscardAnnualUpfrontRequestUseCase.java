package com.red_escuela.backend.billing.annualupfront.usecase;

import com.red_escuela.backend.billing.annualupfront.service.DiscardedAnnualUpfrontRequest;

public interface DiscardAnnualUpfrontRequestUseCase {

    DiscardedAnnualUpfrontRequest discardAnnualUpfrontRequestOfCurrentStudent(Long requestId);
}
