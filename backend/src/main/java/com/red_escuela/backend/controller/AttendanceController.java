package com.red_escuela.backend.controller;

import com.red_escuela.backend.dto.AttendanceHistoryResponse;
import com.red_escuela.backend.dto.AttendanceResponse;
import com.red_escuela.backend.dto.AttendanceRiskResponse;
import com.red_escuela.backend.dto.CargarAsistenciaRequest;
import com.red_escuela.backend.dto.ClassSessionResponse;
import com.red_escuela.backend.dto.JustificarAsistenciaRequest;
import com.red_escuela.backend.dto.RosterEntryResponse;
import com.red_escuela.backend.academicstructure.entity.Instructor;
import com.red_escuela.backend.service.AttendanceExportService;
import com.red_escuela.backend.service.AttendanceService;
import com.red_escuela.backend.service.CurrentInstructorService;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * RF-DO-03/04, RF-PR-08, RF-PR-12. Primera vez en el sistema donde un controller no puede confiar
 * únicamente en @PreAuthorize por rol: la pertenencia del Docente a la comisión (¿es propia?)
 * la valida el service (AttendanceService.cargarAsistencia*, NotOwnSectionException).
 */
@RestController
public class AttendanceController {

    private final AttendanceService attendanceService;
    private final AttendanceExportService attendanceExportService;
    private final CurrentInstructorService currentInstructorService;

    public AttendanceController(
            AttendanceService attendanceService,
            AttendanceExportService attendanceExportService,
            CurrentInstructorService currentInstructorService) {
        this.attendanceService = attendanceService;
        this.attendanceExportService = attendanceExportService;
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
    @PreAuthorize("hasRole('DOCENTE') or hasRole('PRECEPTOR') or hasRole('ADMINISTRATIVO')")
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

    @GetMapping("/program-sections/{id}/roster")
    @PreAuthorize("hasRole('DOCENTE')")
    public List<RosterEntryResponse> rosterProgramSection(@PathVariable Long id) {
        Instructor instructor = currentInstructorService.resolveCurrentInstructor();
        return attendanceService.obtenerRosterProgramSection(id, instructor.getId());
    }

    @GetMapping("/course-sections/{id}/roster")
    @PreAuthorize("hasRole('DOCENTE')")
    public List<RosterEntryResponse> rosterCourseSection(@PathVariable Long id) {
        Instructor instructor = currentInstructorService.resolveCurrentInstructor();
        return attendanceService.obtenerRosterCourseSection(id, instructor.getId());
    }

    @GetMapping("/program-sections/{id}/class-sessions/{date}")
    @PreAuthorize("hasRole('DOCENTE')")
    public ClassSessionResponse claseProgramSection(@PathVariable Long id, @PathVariable LocalDate date) {
        Instructor instructor = currentInstructorService.resolveCurrentInstructor();
        return ClassSessionResponse.from(attendanceService.obtenerClaseProgramSection(id, instructor.getId(), date));
    }

    @GetMapping("/course-sections/{id}/class-sessions/{date}")
    @PreAuthorize("hasRole('DOCENTE')")
    public ClassSessionResponse claseCourseSection(@PathVariable Long id, @PathVariable LocalDate date) {
        Instructor instructor = currentInstructorService.resolveCurrentInstructor();
        return ClassSessionResponse.from(attendanceService.obtenerClaseCourseSection(id, instructor.getId(), date));
    }

    @PostMapping("/program-sections/{id}/class-sessions/{date}/cancel")
    @PreAuthorize("hasRole('DOCENTE')")
    public void cancelarClaseProgramSection(@PathVariable Long id, @PathVariable LocalDate date) {
        Instructor instructor = currentInstructorService.resolveCurrentInstructor();
        attendanceService.cancelarClaseProgramSection(id, instructor.getId(), date);
    }

    @PostMapping("/course-sections/{id}/class-sessions/{date}/cancel")
    @PreAuthorize("hasRole('DOCENTE')")
    public void cancelarClaseCourseSection(@PathVariable Long id, @PathVariable LocalDate date) {
        Instructor instructor = currentInstructorService.resolveCurrentInstructor();
        attendanceService.cancelarClaseCourseSection(id, instructor.getId(), date);
    }

    @GetMapping("/program-sections/{id}/attendance/history")
    @PreAuthorize("hasRole('DOCENTE') or hasRole('PRECEPTOR') or hasRole('ADMINISTRATIVO') or hasRole('DIRECTOR')")
    public AttendanceHistoryResponse historialProgramSection(@PathVariable Long id) {
        return attendanceService.obtenerHistorialProgramSection(id, resolveInstructorIdSiEsDocente());
    }

    @GetMapping("/course-sections/{id}/attendance/history")
    @PreAuthorize("hasRole('DOCENTE') or hasRole('PRECEPTOR') or hasRole('ADMINISTRATIVO') or hasRole('DIRECTOR')")
    public AttendanceHistoryResponse historialCourseSection(@PathVariable Long id) {
        return attendanceService.obtenerHistorialCourseSection(id, resolveInstructorIdSiEsDocente());
    }

    @GetMapping("/program-sections/{id}/attendance/absence-report")
    @PreAuthorize("hasRole('DOCENTE') or hasRole('PRECEPTOR') or hasRole('ADMINISTRATIVO') or hasRole('DIRECTOR')")
    public List<RosterEntryResponse> reporteDeAusenciasProgramSection(
            @PathVariable Long id, @RequestParam(required = false) Integer minAbsences) {
        return attendanceService.reporteDeAusenciasProgramSection(id, resolveInstructorIdSiEsDocente(), minAbsences);
    }

    @GetMapping("/course-sections/{id}/attendance/absence-report")
    @PreAuthorize("hasRole('DOCENTE') or hasRole('PRECEPTOR') or hasRole('ADMINISTRATIVO') or hasRole('DIRECTOR')")
    public List<RosterEntryResponse> reporteDeAusenciasCourseSection(
            @PathVariable Long id, @RequestParam(required = false) Integer minAbsences) {
        return attendanceService.reporteDeAusenciasCourseSection(id, resolveInstructorIdSiEsDocente(), minAbsences);
    }

    @GetMapping("/program-sections/{id}/attendance/export")
    @PreAuthorize("hasRole('DOCENTE') or hasRole('PRECEPTOR') or hasRole('ADMINISTRATIVO') or hasRole('DIRECTOR')")
    public ResponseEntity<byte[]> exportarProgramSection(
            @PathVariable Long id, @RequestParam String format, @RequestParam String scope,
            @RequestParam(required = false) LocalDate classDate) {
        return exportar(attendanceExportService.exportarProgramSection(id, resolveInstructorIdSiEsDocente(), scope, format, classDate));
    }

    @GetMapping("/course-sections/{id}/attendance/export")
    @PreAuthorize("hasRole('DOCENTE') or hasRole('PRECEPTOR') or hasRole('ADMINISTRATIVO') or hasRole('DIRECTOR')")
    public ResponseEntity<byte[]> exportarCourseSection(
            @PathVariable Long id, @RequestParam String format, @RequestParam String scope,
            @RequestParam(required = false) LocalDate classDate) {
        return exportar(attendanceExportService.exportarCourseSection(id, resolveInstructorIdSiEsDocente(), scope, format, classDate));
    }

    private ResponseEntity<byte[]> exportar(AttendanceExportService.ExportedFile file) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + file.filename() + "\"")
                .body(file.content());
    }

    private Long resolveInstructorIdSiEsDocente() {
        boolean esDocente = SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_DOCENTE"));
        return esDocente ? currentInstructorService.resolveCurrentInstructor().getId() : null;
    }
}
