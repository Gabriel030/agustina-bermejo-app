package com.red_escuela.backend.academicstructure.service;

import com.red_escuela.backend.academicstructure.api.CourseOccupiedSeatsPort;
import com.red_escuela.backend.academicstructure.dto.CourseResponse;
import org.springframework.stereotype.Service;

@Service
public class CourseDetailReader {

    private final CourseService courseService;
    private final CourseOccupiedSeatsPort courseOccupiedSeatsPort;

    public CourseDetailReader(CourseService courseService, CourseOccupiedSeatsPort courseOccupiedSeatsPort) {
        this.courseService = courseService;
        this.courseOccupiedSeatsPort = courseOccupiedSeatsPort;
    }

    public CourseResponse read(Long courseId) {
        return CourseResponse.from(courseService.get(courseId), courseOccupiedSeatsPort.countOccupiedSeats(courseId));
    }
}
