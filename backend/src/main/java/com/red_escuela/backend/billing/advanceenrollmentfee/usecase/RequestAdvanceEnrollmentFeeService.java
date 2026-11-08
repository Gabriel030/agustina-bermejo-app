package com.red_escuela.backend.billing.advanceenrollmentfee.usecase;

import com.red_escuela.backend.billing.advanceenrollmentfee.service.AdvanceEnrollmentFeeIssuer;
import com.red_escuela.backend.billing.advanceenrollmentfee.service.AdvanceEnrollmentFeeIssuer.AdvanceEnrollmentFeeIssuance;
import com.red_escuela.backend.shared.UseCase;
import org.springframework.security.access.prepost.PreAuthorize;

@UseCase
public class RequestAdvanceEnrollmentFeeService implements RequestAdvanceEnrollmentFeeUseCase {
    private final AdvanceEnrollmentFeeIssuer issuer;

    public RequestAdvanceEnrollmentFeeService(AdvanceEnrollmentFeeIssuer issuer) {
        this.issuer = issuer;
    }

    @Override
    @PreAuthorize("hasRole('ALUMNO')")
    public AdvanceEnrollmentFeeIssuance requestForCurrentStudent(Long programEnrollmentId) {
        return issuer.issueForCurrentStudent(programEnrollmentId);
    }

    @Override
    @PreAuthorize("hasAnyRole('ADMINISTRATIVO', 'DIRECTOR')")
    public AdvanceEnrollmentFeeIssuance requestForStudentAtCounter(Long programEnrollmentId) {
        return issuer.issueForStaff(programEnrollmentId);
    }
}
