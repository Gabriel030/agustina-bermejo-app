package com.red_escuela.backend.enrollment.usecase.directorapproval;

import com.red_escuela.backend.enrollment.dto.CreateDirectorApprovalRequestRequest;
import com.red_escuela.backend.enrollment.entity.DirectorApprovalRequest;
import com.red_escuela.backend.enrollment.service.RequestDirectorApprovalTransaction;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class RequestDirectorApprovalService implements RequestDirectorApprovalUseCase {

    private final RequestDirectorApprovalTransaction requestDirectorApprovalTransaction;

    public RequestDirectorApprovalService(RequestDirectorApprovalTransaction requestDirectorApprovalTransaction) {
        this.requestDirectorApprovalTransaction = requestDirectorApprovalTransaction;
    }

    @Override
    public DirectorApprovalRequest request(CreateDirectorApprovalRequestRequest request) {
        return requestDirectorApprovalTransaction.request(request);
    }
}
