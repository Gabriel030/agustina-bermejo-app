package com.red_escuela.backend.academicstructure.service;

import com.red_escuela.backend.academicstructure.dto.CoursePricingResponse;
import org.springframework.stereotype.Service;

@Service
public class CoursePricingReader {

    private final CourseService courseService;

    public CoursePricingReader(CourseService courseService) {
        this.courseService = courseService;
    }

    public CoursePricingResponse read(Long courseId) {
        return new CoursePricingResponse(courseId, courseService.get(courseId).priceAsMoney());
    }
}
