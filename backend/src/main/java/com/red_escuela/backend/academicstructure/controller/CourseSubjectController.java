package com.red_escuela.backend.academicstructure.controller;

import com.red_escuela.backend.academicstructure.dto.CourseSubjectResponse;
import com.red_escuela.backend.academicstructure.dto.CreateCourseSubjectRequest;
import com.red_escuela.backend.academicstructure.dto.UpdateCourseSubjectRequest;
import com.red_escuela.backend.entity.StatusType;
import com.red_escuela.backend.academicstructure.service.CourseSubjectService;
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
    public CourseSubjectResponse agregarModulo(@PathVariable Long id, @Valid @RequestBody CreateCourseSubjectRequest request) {
        return CourseSubjectResponse.from(courseSubjectService.agregarModulo(id, request));
    }

    @PutMapping("/course-subjects/{id}")
    @PreAuthorize("hasRole('ADMINISTRATIVO')")
    public CourseSubjectResponse editarModulo(@PathVariable Long id, @RequestBody UpdateCourseSubjectRequest request) {
        return CourseSubjectResponse.from(courseSubjectService.editarModulo(id, request));
    }

    @GetMapping("/courses/{id}/subjects")
    public List<CourseSubjectResponse> listarPorCurso(@PathVariable Long id, @RequestParam(required = false) StatusType status) {
        return courseSubjectService.listarPorCurso(id, status).stream().map(CourseSubjectResponse::from).toList();
    }

    @GetMapping("/course-subjects/{id}")
    public CourseSubjectResponse verDetalle(@PathVariable Long id) {
        return CourseSubjectResponse.from(courseSubjectService.verDetalle(id));
    }

    @PostMapping("/course-subjects/{id}/deactivate")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMINISTRATIVO')")
    public void desactivar(@PathVariable Long id) {
        courseSubjectService.desactivar(id);
    }

    @PostMapping("/course-subjects/{id}/activate")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMINISTRATIVO')")
    public void reactivar(@PathVariable Long id) {
        courseSubjectService.reactivar(id);
    }
}
