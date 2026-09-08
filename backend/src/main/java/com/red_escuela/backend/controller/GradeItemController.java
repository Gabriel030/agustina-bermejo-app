package com.red_escuela.backend.controller;

import com.red_escuela.backend.dto.CreateGradeItemRequest;
import com.red_escuela.backend.dto.GradeItemResponse;
import com.red_escuela.backend.academicstructure.entity.Instructor;
import com.red_escuela.backend.service.CurrentInstructorService;
import com.red_escuela.backend.service.GradeItemService;
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

/**
 * RF-DO-05/06/07, RF-AL-07. La carga dispara el recálculo automático de status/finalScore de
 * la cursada (GradeItemService.recalcularEstadoCursada), nunca lo decide el Docente a mano.
 */
@RestController
public class GradeItemController {

    private final GradeItemService gradeItemService;
    private final CurrentInstructorService currentInstructorService;

    public GradeItemController(GradeItemService gradeItemService, CurrentInstructorService currentInstructorService) {
        this.gradeItemService = gradeItemService;
        this.currentInstructorService = currentInstructorService;
    }

    @PostMapping("/section-enrollments/{id}/grade-items")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('DOCENTE')")
    public GradeItemResponse cargarNotaProgramEnrollment(@PathVariable Long id, @Valid @RequestBody CreateGradeItemRequest request) {
        Instructor instructor = currentInstructorService.resolveCurrentInstructor();
        return GradeItemResponse.from(gradeItemService.cargarNotaProgramEnrollment(id, instructor.getId(), request));
    }

    @PostMapping("/course-section-enrollments/{id}/grade-items")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('DOCENTE')")
    public GradeItemResponse cargarNotaCourseEnrollment(@PathVariable Long id, @Valid @RequestBody CreateGradeItemRequest request) {
        Instructor instructor = currentInstructorService.resolveCurrentInstructor();
        return GradeItemResponse.from(gradeItemService.cargarNotaCourseEnrollment(id, instructor.getId(), request));
    }

    @GetMapping("/section-enrollments/{id}/grade-items")
    @PreAuthorize("hasRole('PRECEPTOR') or hasRole('ADMINISTRATIVO') or hasRole('DIRECTOR')")
    public List<GradeItemResponse> listarNotasProgramEnrollment(@PathVariable Long id) {
        return gradeItemService.listarPorProgramEnrollment(id).stream().map(GradeItemResponse::from).toList();
    }

    @GetMapping("/course-section-enrollments/{id}/grade-items")
    @PreAuthorize("hasRole('PRECEPTOR') or hasRole('ADMINISTRATIVO') or hasRole('DIRECTOR')")
    public List<GradeItemResponse> listarNotasCourseEnrollment(@PathVariable Long id) {
        return gradeItemService.listarPorCourseEnrollment(id).stream().map(GradeItemResponse::from).toList();
    }
}
