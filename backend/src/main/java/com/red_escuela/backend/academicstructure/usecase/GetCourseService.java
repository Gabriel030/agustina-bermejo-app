package com.red_escuela.backend.academicstructure.usecase;

import com.red_escuela.backend.academicstructure.dto.CourseResponse;
import com.red_escuela.backend.academicstructure.service.CourseDetailReader;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class GetCourseService implements GetCourseUseCase {

    private final CourseDetailReader courseDetailReader;

    public GetCourseService(CourseDetailReader courseDetailReader) {
        this.courseDetailReader = courseDetailReader;
    }

    @Override
    public CourseResponse execute(Long courseId) {
        return courseDetailReader.read(courseId);
    }
}
