package com.red_escuela.backend.billing.authorization.usecase;

import com.red_escuela.backend.billing.authorization.domain.AuthorizationRequest;

public interface RejectAuthorizationRequestUseCase {
    AuthorizationRequest reject(Long id, String decisionReason);
}
