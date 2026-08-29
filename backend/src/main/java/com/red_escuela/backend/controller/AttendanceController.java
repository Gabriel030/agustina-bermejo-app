package com.red_escuela.backend.controller;

import com.red_escuela.backend.dto.AttendanceResponse;
import com.red_escuela.backend.dto.AttendanceRiskResponse;
import com.red_escuela.backend.dto.CargarAsistenciaRequest;
import com.red_escuela.backend.dto.JustificarAsistenciaRequest;
import com.red_escuela.backend.entity.Instructor;
import com.red_escuela.backend.service.AttendanceService;
import com.red_escuela.backend.service.CurrentInstructorService;
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
 * RF-DO-03/04, RF-PR-08/12. Primera vez en el sistema donde un controller no puede confiar
 * únicamente en @PreAuthorize por rol: la pertenencia del Docente a la comisión (¿es propia?)
 * la valida el service (AttendanceService.cargarAsistencia*, NotOwnSectionException).
 */
@RestController
public class AttendanceController {

    private final AttendanceService attendanceService;
    private final CurrentInstructorService currentInstructorService;

    public AttendanceController(AttendanceService attendanceService, CurrentInstructorService currentInstructorService) {
        this.attendanceService = attendanceService;
        this.currentInstructorService = currentInstructorService;
    }

    @PostMapping("/program-sections/{id}/attendance")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('DOCENTE')")
    public List<AttendanceResponse> cargarAsistenciaProgramSection(
            @PathVariable Long id, @Valid @RequestBody CargarAsistenciaRequest request) {
        Instructor instructor = currentInstructorService.resolveCurrentInstructor();
        return attendanceService.cargarAsistenciaProgramSection(id, instructor.getId(), request).stream()
                .map(AttendanceResponse::from).toList();
    }

    @PostMapping("/course-sections/{id}/attendance")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('DOCENTE')")
    public List<AttendanceResponse> cargarAsistenciaCourseSection(
            @PathVariable Long id, @Valid @RequestBody CargarAsistenciaRequest request) {
        Instructor instructor = currentInstructorService.resolveCurrentInstructor();
        return attendanceService.cargarAsistenciaCourseSection(id, instructor.getId(), request).stream()
                .map(AttendanceResponse::from).toList();
    }

    @PostMapping("/attendance/{id}/justify")
    @PreAuthorize("hasRole('PRECEPTOR') or hasRole('ADMINISTRATIVO')")
    public AttendanceResponse justificar(@PathVariable Long id, @Valid @RequestBody JustificarAsistenciaRequest request) {
        return AttendanceResponse.from(attendanceService.justificar(id, request.reason(), request.attachmentUrl()));
    }

    @PostMapping("/attendance/{id}/approve-justification")
    @PreAuthorize("hasRole('PRECEPTOR') or hasRole('ADMINISTRATIVO')")
    public AttendanceResponse aprobarJustificacion(@PathVariable Long id) {
        return AttendanceResponse.from(attendanceService.aprobarJustificacion(id));
    }

    @PostMapping("/attendance/{id}/reject-justification")
    @PreAuthorize("hasRole('PRECEPTOR') or hasRole('ADMINISTRATIVO')")
    public AttendanceResponse rechazarJustificacion(@PathVariable Long id) {
        return AttendanceResponse.from(attendanceService.rechazarJustificacion(id));
    }

    @GetMapping("/section-enrollments/{id}/attendance")
    @PreAuthorize("hasRole('PRECEPTOR') or hasRole('ADMINISTRATIVO') or hasRole('DIRECTOR')")
    public List<AttendanceResponse> listarAsistenciaProgramEnrollment(@PathVariable Long id) {
        return attendanceService.listarPorProgramEnrollment(id).stream().map(AttendanceResponse::from).toList();
    }

    @GetMapping("/course-section-enrollments/{id}/attendance")
    @PreAuthorize("hasRole('PRECEPTOR') or hasRole('ADMINISTRATIVO') or hasRole('DIRECTOR')")
    public List<AttendanceResponse> listarAsistenciaCourseEnrollment(@PathVariable Long id) {
        return attendanceService.listarPorCourseEnrollment(id).stream().map(AttendanceResponse::from).toList();
    }

    @GetMapping("/section-enrollments/{id}/attendance/risk")
    @PreAuthorize("hasRole('PRECEPTOR') or hasRole('ADMINISTRATIVO') or hasRole('DIRECTOR')")
    public AttendanceRiskResponse riesgoProgramEnrollment(@PathVariable Long id) {
        return attendanceService.calcularRiesgoDeLibrePorProgramEnrollment(id);
    }

    @GetMapping("/course-section-enrollments/{id}/attendance/risk")
    @PreAuthorize("hasRole('PRECEPTOR') or hasRole('ADMINISTRATIVO') or hasRole('DIRECTOR')")
    public AttendanceRiskResponse riesgoCourseEnrollment(@PathVariable Long id) {
        return attendanceService.calcularRiesgoDeLibrePorCourseEnrollment(id);
    }
}
