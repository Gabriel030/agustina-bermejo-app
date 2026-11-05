package com.red_escuela.backend.billing.usecase.invoice;

import com.red_escuela.backend.billing.dto.AdminEnrollmentBillingStatusResponse;
import com.red_escuela.backend.billing.reporting.service.StudentBillingOverviewReader;
import com.red_escuela.backend.shared.UseCase;
import java.util.List;

@UseCase
public class GetStudentBillingOverviewService implements GetStudentBillingOverviewUseCase {
    private final StudentBillingOverviewReader studentBillingOverviewReader;

    public GetStudentBillingOverviewService(StudentBillingOverviewReader studentBillingOverviewReader) {
        this.studentBillingOverviewReader = studentBillingOverviewReader;
    }

    @Override
    public List<AdminEnrollmentBillingStatusResponse> get(Long studentId) {
        return studentBillingOverviewReader.readStudentBillingOverview(studentId);
    }
}
