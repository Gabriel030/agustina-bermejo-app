package com.red_escuela.backend.billing.authorization.usecase;

import com.red_escuela.backend.billing.authorization.domain.AuthorizationRequest;
import com.red_escuela.backend.billing.authorization.service.AuthorizationRequestDecider;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class RejectAuthorizationRequestService implements RejectAuthorizationRequestUseCase {
    private final AuthorizationRequestDecider decider;

    public RejectAuthorizationRequestService(AuthorizationRequestDecider decider) {
        this.decider = decider;
    }

    @Override
    public AuthorizationRequest reject(Long id, String decisionReason) {
        return decider.reject(id, decisionReason);
    }
}
