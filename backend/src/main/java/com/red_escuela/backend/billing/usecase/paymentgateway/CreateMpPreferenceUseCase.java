package com.red_escuela.backend.billing.usecase.paymentgateway;

import com.red_escuela.backend.billing.dto.MpPreferenceResponse;

public interface CreateMpPreferenceUseCase {

    MpPreferenceResponse create(Long invoiceId, Long studentId);
}
