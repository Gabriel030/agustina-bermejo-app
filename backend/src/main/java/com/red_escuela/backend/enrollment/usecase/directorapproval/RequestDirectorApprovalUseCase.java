package com.red_escuela.backend.enrollment.usecase.directorapproval;

import com.red_escuela.backend.enrollment.dto.CreateDirectorApprovalRequestRequest;
import com.red_escuela.backend.enrollment.entity.DirectorApprovalRequest;

public interface RequestDirectorApprovalUseCase {
    DirectorApprovalRequest request(CreateDirectorApprovalRequestRequest request);
}
