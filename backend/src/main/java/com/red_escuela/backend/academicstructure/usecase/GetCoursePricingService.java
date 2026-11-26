package com.red_escuela.backend.academicstructure.usecase;

import com.red_escuela.backend.academicstructure.dto.CoursePricingResponse;
import com.red_escuela.backend.academicstructure.service.CoursePricingReader;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class GetCoursePricingService implements GetCoursePricingUseCase {

    private final CoursePricingReader coursePricingReader;

    public GetCoursePricingService(CoursePricingReader coursePricingReader) {
        this.coursePricingReader = coursePricingReader;
    }

    @Override
    public CoursePricingResponse execute(Long courseId) {
        return coursePricingReader.read(courseId);
    }
}
