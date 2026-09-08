package com.red_escuela.backend.enrollment.usecase.directorapproval;

import com.red_escuela.backend.enrollment.entity.DirectorApprovalRequest;

public interface GetDirectorApprovalRequestUseCase {
    DirectorApprovalRequest get(Long id);
}
