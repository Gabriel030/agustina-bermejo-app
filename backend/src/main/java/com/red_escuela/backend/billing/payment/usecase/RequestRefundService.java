package com.red_escuela.backend.billing.payment.usecase;

import com.red_escuela.backend.billing.payment.domain.RefundRequestResult;
import com.red_escuela.backend.billing.payment.dto.RequestRefundRequest;
import com.red_escuela.backend.billing.payment.service.RefundOperationRunner;
import com.red_escuela.backend.shared.UseCase;
import org.springframework.security.access.prepost.PreAuthorize;

@UseCase
public class RequestRefundService implements RequestRefundUseCase {
    private final RefundOperationRunner operationRunner;

    public RequestRefundService(RefundOperationRunner operationRunner) {
        this.operationRunner = operationRunner;
    }

    @Override
    @PreAuthorize("hasRole('ALUMNO')")
    public RefundRequestResult requestOwnRefund(Long studentId, RequestRefundRequest request) {
        return operationRunner.runRefundRequest(studentId, request.amount(), request.destinationAccount(), request.destinationHolder());
    }

    @Override
    @PreAuthorize("hasAnyRole('ADMINISTRATIVO', 'DIRECTOR')")
    public RefundRequestResult requestRefundOnBehalfOfStudent(Long studentId, RequestRefundRequest request) {
        return operationRunner.runRefundRequest(studentId, request.amount(), request.destinationAccount(), request.destinationHolder());
    }
}
