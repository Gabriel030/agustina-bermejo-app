package com.red_escuela.backend.billing.usecase.invoice;

import com.red_escuela.backend.billing.dto.AdminEnrollmentBillingStatusResponse;

import java.util.List;

public interface GetStudentBillingOverviewUseCase {
    List<AdminEnrollmentBillingStatusResponse> get(Long studentId);
}
