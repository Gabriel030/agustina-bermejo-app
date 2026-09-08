package com.red_escuela.backend.academicstructure.controller;

import com.red_escuela.backend.academicstructure.dto.CourseResponse;
import com.red_escuela.backend.academicstructure.dto.CreateCourseRequest;
import com.red_escuela.backend.academicstructure.dto.UpdateCourseRequest;
import com.red_escuela.backend.entity.StatusType;
import com.red_escuela.backend.academicstructure.service.CourseService;
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
    public CourseResponse crearCurso(@Valid @RequestBody CreateCourseRequest request) {
        return CourseResponse.from(courseService.crearCurso(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMINISTRATIVO')")
    public CourseResponse editarCurso(@PathVariable Long id, @RequestBody UpdateCourseRequest request) {
        return CourseResponse.from(courseService.editarCurso(id, request));
    }

    @PostMapping("/{id}/deactivate")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMINISTRATIVO')")
    public void desactivar(@PathVariable Long id) {
        courseService.desactivar(id);
    }

    @PostMapping("/{id}/activate")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMINISTRATIVO')")
    public void reactivar(@PathVariable Long id) {
        courseService.reactivar(id);
    }

    @GetMapping
    public List<CourseResponse> listar(@RequestParam(required = false) StatusType status) {
        return courseService.listar(status).stream().map(CourseResponse::from).toList();
    }

    @GetMapping("/{id}")
    public CourseResponse verDetalle(@PathVariable Long id) {
        return CourseResponse.from(courseService.verDetalle(id));
    }
}
