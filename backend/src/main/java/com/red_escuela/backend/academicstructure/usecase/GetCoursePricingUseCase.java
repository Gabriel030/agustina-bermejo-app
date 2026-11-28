package com.red_escuela.backend.academicstructure.usecase;

import com.red_escuela.backend.academicstructure.dto.CoursePricingResponse;

public interface GetCoursePricingUseCase {
    CoursePricingResponse execute(Long courseId);
}
