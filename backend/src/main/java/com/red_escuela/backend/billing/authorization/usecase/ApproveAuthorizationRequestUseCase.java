package com.red_escuela.backend.billing.authorization.usecase;

import com.red_escuela.backend.billing.authorization.domain.AuthorizationRequest;

public interface ApproveAuthorizationRequestUseCase {
    AuthorizationRequest approve(Long id, String decisionReason);
}
