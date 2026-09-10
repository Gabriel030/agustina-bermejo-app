package com.red_escuela.backend.enrollment.usecase.directorapproval;

import com.red_escuela.backend.enrollment.entity.DirectorApprovalRequest;

public interface RejectDirectorApprovalRequestUseCase {
    DirectorApprovalRequest reject(DecideDirectorApprovalRequestCommand command);
}
