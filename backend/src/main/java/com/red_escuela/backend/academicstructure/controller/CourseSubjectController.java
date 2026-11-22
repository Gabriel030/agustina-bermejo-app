package com.red_escuela.backend.academicstructure.controller;

import com.red_escuela.backend.academicstructure.dto.CourseSubjectResponse;
import com.red_escuela.backend.academicstructure.dto.CreateCourseSubjectRequest;
import com.red_escuela.backend.academicstructure.dto.UpdateCourseSubjectRequest;
import com.red_escuela.backend.shared.domain.StatusType;
import com.red_escuela.backend.academicstructure.service.CourseSubjectService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import com.red_escuela.backend.shared.permission.RequiresPermission;
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
    @RequiresPermission(code = "academicstructure.manage_courses", legacyRoles = "ROLE_ADMINISTRATIVO")
    public CourseSubjectResponse addModule(@PathVariable Long id, @Valid @RequestBody CreateCourseSubjectRequest request) {
        return CourseSubjectResponse.from(courseSubjectService.addModule(id, request));
    }

    @PutMapping("/course-subjects/{id}")
    @RequiresPermission(code = "academicstructure.manage_courses", legacyRoles = "ROLE_ADMINISTRATIVO")
    public CourseSubjectResponse update(@PathVariable Long id, @RequestBody UpdateCourseSubjectRequest request) {
        return CourseSubjectResponse.from(courseSubjectService.update(id, request));
    }

    @GetMapping("/courses/{id}/subjects")
    public List<CourseSubjectResponse> listByCourse(@PathVariable Long id, @RequestParam(required = false) StatusType status) {
        return courseSubjectService.listByCourse(id, status).stream().map(CourseSubjectResponse::from).toList();
    }

    @GetMapping("/course-subjects/{id}")
    public CourseSubjectResponse get(@PathVariable Long id) {
        return CourseSubjectResponse.from(courseSubjectService.get(id));
    }

    @PostMapping("/course-subjects/{id}/deactivate")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @RequiresPermission(code = "academicstructure.change_course_status", legacyRoles = "ROLE_ADMINISTRATIVO")
    public void deactivate(@PathVariable Long id) {
        courseSubjectService.deactivate(id);
    }

    @PostMapping("/course-subjects/{id}/activate")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @RequiresPermission(code = "academicstructure.change_course_status", legacyRoles = "ROLE_ADMINISTRATIVO")
    public void reactivate(@PathVariable Long id) {
        courseSubjectService.reactivate(id);
    }
}
