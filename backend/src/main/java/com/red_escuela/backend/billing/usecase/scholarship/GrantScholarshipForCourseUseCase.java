package com.red_escuela.backend.billing.usecase.scholarship;

import com.red_escuela.backend.billing.dto.CreateScholarshipRequest;
import com.red_escuela.backend.billing.entity.Scholarship;

public interface GrantScholarshipForCourseUseCase {
    Scholarship grant(Long courseEnrollmentId, CreateScholarshipRequest request);
}
