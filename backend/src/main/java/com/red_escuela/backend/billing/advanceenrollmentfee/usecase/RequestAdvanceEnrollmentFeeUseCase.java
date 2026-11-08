package com.red_escuela.backend.billing.advanceenrollmentfee.usecase;

import com.red_escuela.backend.billing.advanceenrollmentfee.service.AdvanceEnrollmentFeeIssuer.AdvanceEnrollmentFeeIssuance;

public interface RequestAdvanceEnrollmentFeeUseCase {

    AdvanceEnrollmentFeeIssuance requestForCurrentStudent(Long programEnrollmentId);

    AdvanceEnrollmentFeeIssuance requestForStudentAtCounter(Long programEnrollmentId);
}
