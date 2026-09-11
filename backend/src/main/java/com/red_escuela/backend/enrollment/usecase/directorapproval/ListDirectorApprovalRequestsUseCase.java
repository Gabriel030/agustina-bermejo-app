package com.red_escuela.backend.enrollment.usecase.directorapproval;

import com.red_escuela.backend.enrollment.entity.DirectorApprovalRequest;
import com.red_escuela.backend.enrollment.entity.DirectorApprovalRequestStatus;

import java.util.List;

public interface ListDirectorApprovalRequestsUseCase {
    List<DirectorApprovalRequest> list(DirectorApprovalRequestStatus status);
}
