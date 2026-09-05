package com.red_escuela.backend.controller;

import com.red_escuela.backend.dto.CourseEnrollmentResponse;
import com.red_escuela.backend.dto.CourseProgressResponse;
import com.red_escuela.backend.dto.CreateCourseEnrollmentRequest;
import com.red_escuela.backend.service.CourseEnrollmentService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class CourseEnrollmentController {

    private final CourseEnrollmentService courseEnrollmentService;

    public CourseEnrollmentController(CourseEnrollmentService courseEnrollmentService) {
        this.courseEnrollmentService = courseEnrollmentService;
    }

    @PostMapping("/students/{id}/course-enrollments")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('PRECEPTOR') or hasRole('ADMINISTRATIVO')")
    public CourseEnrollmentResponse enroll(@PathVariable Long id, @Valid @RequestBody CreateCourseEnrollmentRequest request) {
        return CourseEnrollmentResponse.from(courseEnrollmentService.enroll(id, request));
    }

    @PostMapping("/course-enrollments/{id}/withdraw")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('PRECEPTOR') or hasRole('ADMINISTRATIVO')")
    public void withdraw(@PathVariable Long id) {
        courseEnrollmentService.withdraw(id);
    }

    @GetMapping("/students/{id}/course-enrollments")
    @PreAuthorize("hasRole('PRECEPTOR') or hasRole('ADMINISTRATIVO') or hasRole('DIRECTOR')")
    public List<CourseEnrollmentResponse> listByStudent(@PathVariable Long id) {
        return courseEnrollmentService.listByStudent(id).stream().map(CourseEnrollmentResponse::from).toList();
    }

    @GetMapping("/course-enrollments/{id}")
    @PreAuthorize("hasRole('PRECEPTOR') or hasRole('ADMINISTRATIVO') or hasRole('DIRECTOR')")
    public CourseEnrollmentResponse verDetalle(@PathVariable Long id) {
        return CourseEnrollmentResponse.from(courseEnrollmentService.verDetalle(id));
    }

    @GetMapping("/course-enrollments/{id}/progress")
    @PreAuthorize("hasRole('PRECEPTOR') or hasRole('ADMINISTRATIVO') or hasRole('DIRECTOR')")
    public CourseProgressResponse verProgreso(@PathVariable Long id) {
        return courseEnrollmentService.calculateProgress(id);
    }
}
