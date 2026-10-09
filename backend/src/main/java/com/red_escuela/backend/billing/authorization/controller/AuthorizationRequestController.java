package com.red_escuela.backend.billing.authorization.controller;

import com.red_escuela.backend.billing.authorization.dto.AuthorizationRequestDecisionRequest;
import com.red_escuela.backend.billing.authorization.dto.AuthorizationRequestResponse;
import com.red_escuela.backend.billing.authorization.usecase.ApproveAuthorizationRequestUseCase;
import com.red_escuela.backend.billing.authorization.usecase.RejectAuthorizationRequestUseCase;
import com.red_escuela.backend.billing.authorization.usecase.WithdrawAuthorizationRequestUseCase;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AuthorizationRequestController {
    private final ApproveAuthorizationRequestUseCase approveUseCase;
    private final RejectAuthorizationRequestUseCase rejectUseCase;
    private final WithdrawAuthorizationRequestUseCase withdrawUseCase;

    public AuthorizationRequestController(ApproveAuthorizationRequestUseCase approveUseCase, RejectAuthorizationRequestUseCase rejectUseCase,
                                          WithdrawAuthorizationRequestUseCase withdrawUseCase) {
        this.approveUseCase = approveUseCase;
        this.rejectUseCase = rejectUseCase;
        this.withdrawUseCase = withdrawUseCase;
    }

    @PostMapping("/authorization-requests/{id}/approve")
    public AuthorizationRequestResponse approve(@PathVariable Long id, @Valid @RequestBody(required = false) AuthorizationRequestDecisionRequest request) {
        return AuthorizationRequestResponse.from(approveUseCase.approve(id, reasonOf(request)));
    }

    @PostMapping("/authorization-requests/{id}/reject")
    public AuthorizationRequestResponse reject(@PathVariable Long id, @Valid @RequestBody(required = false) AuthorizationRequestDecisionRequest request) {
        return AuthorizationRequestResponse.from(rejectUseCase.reject(id, reasonOf(request)));
    }

    @PostMapping("/authorization-requests/{id}/withdraw")
    public AuthorizationRequestResponse withdraw(@PathVariable Long id) {
        return AuthorizationRequestResponse.from(withdrawUseCase.withdraw(id));
    }

    private String reasonOf(AuthorizationRequestDecisionRequest request) {
        return request == null ? null : request.decisionReason();
    }
}
