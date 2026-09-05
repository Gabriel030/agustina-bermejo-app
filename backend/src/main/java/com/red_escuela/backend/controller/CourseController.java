package com.red_escuela.backend.controller;

import com.red_escuela.backend.dto.CourseResponse;
import com.red_escuela.backend.dto.CreateCourseRequest;
import com.red_escuela.backend.dto.UpdateCourseRequest;
import com.red_escuela.backend.entity.StatusType;
import com.red_escuela.backend.service.CourseService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/courses")
public class CourseController {

    private final CourseService courseService;

    public CourseController(CourseService courseService) {
        this.courseService = courseService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMINISTRATIVO')")
    public CourseResponse createCourse(@Valid @RequestBody CreateCourseRequest request) {
        return CourseResponse.from(courseService.createCourse(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMINISTRATIVO')")
    public CourseResponse updateCourse(@PathVariable Long id, @RequestBody UpdateCourseRequest request) {
        return CourseResponse.from(courseService.updateCourse(id, request));
    }

    @PostMapping("/{id}/deactivate")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMINISTRATIVO')")
    public void deactivate(@PathVariable Long id) {
        courseService.deactivate(id);
    }

    @PostMapping("/{id}/activate")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMINISTRATIVO')")
    public void reactivate(@PathVariable Long id) {
        courseService.reactivate(id);
    }

    @GetMapping
    public List<CourseResponse> list(@RequestParam(required = false) StatusType status) {
        return courseService.list(status).stream().map(CourseResponse::from).toList();
    }

    @GetMapping("/{id}")
    public CourseResponse viewDetail(@PathVariable Long id) {
        return CourseResponse.from(courseService.viewDetail(id));
    }
}
