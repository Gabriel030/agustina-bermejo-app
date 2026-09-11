package com.red_escuela.backend.enrollment.usecase.directorapproval;

import com.red_escuela.backend.enrollment.entity.DirectorApprovalRequest;
import com.red_escuela.backend.enrollment.service.DirectorApprovalRequestFinder;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class GetDirectorApprovalRequestService implements GetDirectorApprovalRequestUseCase {

    private final DirectorApprovalRequestFinder directorApprovalRequestFinder;

    public GetDirectorApprovalRequestService(DirectorApprovalRequestFinder directorApprovalRequestFinder) {
        this.directorApprovalRequestFinder = directorApprovalRequestFinder;
    }

    @Override
    public DirectorApprovalRequest get(Long id) {
        return directorApprovalRequestFinder.findOrThrow(id);
    }
}
