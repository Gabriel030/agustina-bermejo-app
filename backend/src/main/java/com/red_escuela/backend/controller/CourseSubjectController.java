package com.red_escuela.backend.controller;

import com.red_escuela.backend.dto.CourseSubjectResponse;
import com.red_escuela.backend.dto.CreateCourseSubjectRequest;
import com.red_escuela.backend.dto.UpdateCourseSubjectRequest;
import com.red_escuela.backend.entity.StatusType;
import com.red_escuela.backend.service.CourseSubjectService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class CourseSubjectController {

    private final CourseSubjectService courseSubjectService;

    public CourseSubjectController(CourseSubjectService courseSubjectService) {
        this.courseSubjectService = courseSubjectService;
    }

    @PostMapping("/courses/{id}/subjects")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMINISTRATIVO')")
    public CourseSubjectResponse addModule(@PathVariable Long id, @Valid @RequestBody CreateCourseSubjectRequest request) {
        return CourseSubjectResponse.from(courseSubjectService.addModule(id, request));
    }

    @PutMapping("/course-subjects/{id}")
    @PreAuthorize("hasRole('ADMINISTRATIVO')")
    public CourseSubjectResponse updateCourseSubject(@PathVariable Long id, @RequestBody UpdateCourseSubjectRequest request) {
        return CourseSubjectResponse.from(courseSubjectService.updateCourseSubject(id, request));
    }

    @GetMapping("/courses/{id}/subjects")
    public List<CourseSubjectResponse> listByCourse(@PathVariable Long id, @RequestParam(required = false) StatusType status) {
        return courseSubjectService.listByCourse(id, status).stream().map(CourseSubjectResponse::from).toList();
    }

    @GetMapping("/course-subjects/{id}")
    public CourseSubjectResponse viewDetail(@PathVariable Long id) {
        return CourseSubjectResponse.from(courseSubjectService.viewDetail(id));
    }

    @PostMapping("/course-subjects/{id}/deactivate")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMINISTRATIVO')")
    public void deactivate(@PathVariable Long id) {
        courseSubjectService.deactivate(id);
    }

    @PostMapping("/course-subjects/{id}/activate")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMINISTRATIVO')")
    public void reactivate(@PathVariable Long id) {
        courseSubjectService.reactivate(id);
    }
}
