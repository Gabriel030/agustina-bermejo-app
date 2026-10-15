package com.red_escuela.backend.billing.authorization.usecase;

import com.red_escuela.backend.billing.authorization.domain.AuthorizationRequest;
import com.red_escuela.backend.billing.authorization.service.AuthorizationRequestDecider;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class ApproveAuthorizationRequestService implements ApproveAuthorizationRequestUseCase {
    private final AuthorizationRequestDecider decider;

    public ApproveAuthorizationRequestService(AuthorizationRequestDecider decider) {
        this.decider = decider;
    }

    @Override
    public AuthorizationRequest approve(Long id, String decisionReason) {
        return decider.approveAndResume(id, decisionReason);
    }
}
