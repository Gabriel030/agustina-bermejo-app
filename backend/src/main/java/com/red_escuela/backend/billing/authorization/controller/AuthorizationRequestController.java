package com.red_escuela.backend.billing.authorization.controller;

import com.red_escuela.backend.billing.authorization.domain.AuthorizationOperationFamily;
import com.red_escuela.backend.billing.authorization.domain.AuthorizationRequestSearch;
import com.red_escuela.backend.billing.authorization.domain.AuthorizationRequestSort;
import com.red_escuela.backend.billing.authorization.domain.AuthorizationRequestStatus;
import com.red_escuela.backend.billing.authorization.dto.AuthorizationRequestDecisionRequest;
import com.red_escuela.backend.billing.authorization.dto.AuthorizationRequestDetailResponse;
import com.red_escuela.backend.billing.authorization.dto.AuthorizationRequestPageResponse;
import com.red_escuela.backend.billing.authorization.dto.AuthorizationRequestResponse;
import com.red_escuela.backend.billing.authorization.usecase.ApproveAuthorizationRequestUseCase;
import com.red_escuela.backend.billing.authorization.usecase.GetAuthorizationRequestUseCase;
import com.red_escuela.backend.billing.authorization.usecase.ListAuthorizationRequestsUseCase;
import com.red_escuela.backend.billing.authorization.usecase.RejectAuthorizationRequestUseCase;
import com.red_escuela.backend.billing.authorization.usecase.WithdrawAuthorizationRequestUseCase;
import com.red_escuela.backend.billing.operation.BillingOperationType;
import com.red_escuela.backend.shared.module.RequiresModule;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;

@RestController
@RequiresModule(RequiresModule.BILLING)
public class AuthorizationRequestController {
    private final ApproveAuthorizationRequestUseCase approveUseCase;
    private final RejectAuthorizationRequestUseCase rejectUseCase;
    private final WithdrawAuthorizationRequestUseCase withdrawUseCase;
    private final ListAuthorizationRequestsUseCase listUseCase;
    private final GetAuthorizationRequestUseCase getUseCase;

    public AuthorizationRequestController(ApproveAuthorizationRequestUseCase approveUseCase, RejectAuthorizationRequestUseCase rejectUseCase,
                                          WithdrawAuthorizationRequestUseCase withdrawUseCase, ListAuthorizationRequestsUseCase listUseCase,
                                          GetAuthorizationRequestUseCase getUseCase) {
        this.approveUseCase = approveUseCase;
        this.rejectUseCase = rejectUseCase;
        this.withdrawUseCase = withdrawUseCase;
        this.listUseCase = listUseCase;
        this.getUseCase = getUseCase;
    }

    @GetMapping("/authorization-requests")
    public AuthorizationRequestPageResponse list(@RequestParam(required = false) Set<AuthorizationRequestStatus> status,
                                                 @RequestParam(required = false) Set<BillingOperationType> operationType,
                                                 @RequestParam(required = false) Set<AuthorizationOperationFamily> family, @RequestParam(required = false) Long requestedBy,
                                                 @RequestParam(defaultValue = "EXPIRES_ON") AuthorizationRequestSort sort, @RequestParam(defaultValue = "false") boolean descending,
                                                 @RequestParam(defaultValue = "0") int page,
                                                 @RequestParam(defaultValue = "" + AuthorizationRequestSearch.DEFAULT_PAGE_SIZE) int size) {
        return AuthorizationRequestPageResponse.from(listUseCase.listAuthorizationRequests(
                new AuthorizationRequestSearch(status, operationType, family, requestedBy, sort, !descending, page, size)));
    }

    @GetMapping("/authorization-requests/{id}")
    public AuthorizationRequestDetailResponse get(@PathVariable Long id) {
        return AuthorizationRequestDetailResponse.from(getUseCase.getAuthorizationRequest(id));
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
