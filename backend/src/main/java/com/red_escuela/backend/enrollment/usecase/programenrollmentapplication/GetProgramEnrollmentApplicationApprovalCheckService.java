package com.red_escuela.backend.enrollment.usecase.programenrollmentapplication;

import com.red_escuela.backend.enrollment.dto.ProgramEnrollmentApplicationApprovalCheckResponse;
import com.red_escuela.backend.enrollment.service.ProgramApplicationApprovalCheck;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class GetProgramEnrollmentApplicationApprovalCheckService implements GetProgramEnrollmentApplicationApprovalCheckUseCase {

    private final ProgramApplicationApprovalCheck approvalCheck;

    public GetProgramEnrollmentApplicationApprovalCheckService(ProgramApplicationApprovalCheck approvalCheck) {
        this.approvalCheck = approvalCheck;
    }

    @Override
    public ProgramEnrollmentApplicationApprovalCheckResponse get(Long applicationId) {
        return approvalCheck.check(applicationId);
    }
}
