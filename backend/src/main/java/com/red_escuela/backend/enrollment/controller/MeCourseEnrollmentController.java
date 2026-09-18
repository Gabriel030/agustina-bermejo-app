package com.red_escuela.backend.enrollment.controller;

import com.red_escuela.backend.enrollment.dto.CourseEnrollmentResponse;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.enrollment.service.CourseEnrollmentService;
import com.red_escuela.backend.identity.service.CurrentStudentService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/me")
public class MeCourseEnrollmentController {
    private final CurrentStudentService currentStudentService;
    private final CourseEnrollmentService courseEnrollmentService;

    public MeCourseEnrollmentController(CurrentStudentService currentStudentService, CourseEnrollmentService courseEnrollmentService) {
        this.currentStudentService = currentStudentService;
        this.courseEnrollmentService = courseEnrollmentService;
    }

    @GetMapping("/course-enrollments")
    public List<CourseEnrollmentResponse> mine() {
        Student student = currentStudentService.resolveCurrentStudent();
        return courseEnrollmentService.listByStudent(student.getId()).stream().map(CourseEnrollmentResponse::from).toList();
    }
}
