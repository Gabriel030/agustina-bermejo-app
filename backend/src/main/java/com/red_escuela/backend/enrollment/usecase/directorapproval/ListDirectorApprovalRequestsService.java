package com.red_escuela.backend.enrollment.usecase.directorapproval;

import com.red_escuela.backend.enrollment.entity.DirectorApprovalRequest;
import com.red_escuela.backend.enrollment.entity.DirectorApprovalRequestStatus;
import com.red_escuela.backend.enrollment.repository.DirectorApprovalRequestRepository;
import com.red_escuela.backend.shared.UseCase;

import java.util.List;

@UseCase
public class ListDirectorApprovalRequestsService implements ListDirectorApprovalRequestsUseCase {

    private final DirectorApprovalRequestRepository directorApprovalRequestRepository;

    public ListDirectorApprovalRequestsService(DirectorApprovalRequestRepository directorApprovalRequestRepository) {
        this.directorApprovalRequestRepository = directorApprovalRequestRepository;
    }

    @Override
    public List<DirectorApprovalRequest> list(DirectorApprovalRequestStatus status) {
        return directorApprovalRequestRepository.findByStatus(status != null ? status : DirectorApprovalRequestStatus.REQUESTED);
    }
}
