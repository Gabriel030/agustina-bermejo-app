package com.red_escuela.backend.billing.authorization.usecase;

import com.red_escuela.backend.billing.authorization.domain.AuthorizationRequest;
import com.red_escuela.backend.billing.authorization.service.AuthorizationRequestDecider;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class WithdrawAuthorizationRequestService implements WithdrawAuthorizationRequestUseCase {
    private final AuthorizationRequestDecider decider;

    public WithdrawAuthorizationRequestService(AuthorizationRequestDecider decider) {
        this.decider = decider;
    }

    @Override
    public AuthorizationRequest withdraw(Long id) {
        return decider.withdraw(id);
    }
}
