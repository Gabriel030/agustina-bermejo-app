package com.red_escuela.backend.controller;

import com.red_escuela.backend.dto.AttendanceHistoryResponse;
import com.red_escuela.backend.dto.AttendanceResponse;
import com.red_escuela.backend.dto.AttendanceRiskResponse;
import com.red_escuela.backend.dto.CargarAsistenciaRequest;
import com.red_escuela.backend.dto.ClassSessionResponse;
import com.red_escuela.backend.dto.JustificarAsistenciaRequest;
import com.red_escuela.backend.dto.RosterEntryResponse;
import com.red_escuela.backend.entity.Instructor;
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
    public List<AttendanceResponse> loadAttendanceProgramSection(
            @PathVariable Long id, @Valid @RequestBody CargarAsistenciaRequest request) {
        Instructor instructor = currentInstructorService.resolveCurrentInstructor();
        return attendanceService.loadAttendanceProgramSection(id, instructor.getId(), request).stream()
                .map(AttendanceResponse::from).toList();
    }

    @PostMapping("/course-sections/{id}/attendance")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('DOCENTE')")
    public List<AttendanceResponse> loadAttendanceCourseSection(
            @PathVariable Long id, @Valid @RequestBody CargarAsistenciaRequest request) {
        Instructor instructor = currentInstructorService.resolveCurrentInstructor();
        return attendanceService.loadAttendanceCourseSection(id, instructor.getId(), request).stream()
                .map(AttendanceResponse::from).toList();
    }

    @PostMapping("/attendance/{id}/justify")
    @PreAuthorize("hasRole('DOCENTE') or hasRole('PRECEPTOR') or hasRole('ADMINISTRATIVO')")
    public AttendanceResponse justify(@PathVariable Long id, @Valid @RequestBody JustificarAsistenciaRequest request) {
        return AttendanceResponse.from(attendanceService.justify(id, request.reason(), request.attachmentUrl()));
    }

    @PostMapping("/attendance/{id}/approve-justification")
    @PreAuthorize("hasRole('PRECEPTOR') or hasRole('ADMINISTRATIVO')")
    public AttendanceResponse approveJustification(@PathVariable Long id) {
        return AttendanceResponse.from(attendanceService.approveJustification(id));
    }

    @PostMapping("/attendance/{id}/reject-justification")
    @PreAuthorize("hasRole('PRECEPTOR') or hasRole('ADMINISTRATIVO')")
    public AttendanceResponse rejectJustification(@PathVariable Long id) {
        return AttendanceResponse.from(attendanceService.rejectJustification(id));
    }

    @GetMapping("/section-enrollments/{id}/attendance")
    @PreAuthorize("hasRole('PRECEPTOR') or hasRole('ADMINISTRATIVO') or hasRole('DIRECTOR')")
    public List<AttendanceResponse> listAttendanceByProgramEnrollment(@PathVariable Long id) {
        return attendanceService.listByProgramEnrollment(id).stream().map(AttendanceResponse::from).toList();
    }

    @GetMapping("/course-section-enrollments/{id}/attendance")
    @PreAuthorize("hasRole('PRECEPTOR') or hasRole('ADMINISTRATIVO') or hasRole('DIRECTOR')")
    public List<AttendanceResponse> listAttendanceByCourseEnrollment(@PathVariable Long id) {
        return attendanceService.listByCourseEnrollment(id).stream().map(AttendanceResponse::from).toList();
    }

    @GetMapping("/section-enrollments/{id}/attendance/risk")
    @PreAuthorize("hasRole('PRECEPTOR') or hasRole('ADMINISTRATIVO') or hasRole('DIRECTOR')")
    public AttendanceRiskResponse attendanceRiskProgramEnrollment(@PathVariable Long id) {
        return attendanceService.calculateDropoutRiskByProgramEnrollment(id);
    }

    @GetMapping("/course-section-enrollments/{id}/attendance/risk")
    @PreAuthorize("hasRole('PRECEPTOR') or hasRole('ADMINISTRATIVO') or hasRole('DIRECTOR')")
    public AttendanceRiskResponse attendanceRiskCourseEnrollment(@PathVariable Long id) {
        return attendanceService.calculateDropoutRiskByCourseEnrollment(id);
    }

    @GetMapping("/program-sections/{id}/roster")
    @PreAuthorize("hasRole('DOCENTE')")
    public List<RosterEntryResponse> rosterProgramSection(@PathVariable Long id) {
        Instructor instructor = currentInstructorService.resolveCurrentInstructor();
        return attendanceService.getRosterProgramSection(id, instructor.getId());
    }

    @GetMapping("/course-sections/{id}/roster")
    @PreAuthorize("hasRole('DOCENTE')")
    public List<RosterEntryResponse> rosterCourseSection(@PathVariable Long id) {
        Instructor instructor = currentInstructorService.resolveCurrentInstructor();
        return attendanceService.getRosterCourseSection(id, instructor.getId());
    }

    @GetMapping("/program-sections/{id}/class-sessions/{date}")
    @PreAuthorize("hasRole('DOCENTE')")
    public ClassSessionResponse classSessionProgramSection(@PathVariable Long id, @PathVariable LocalDate date) {
        Instructor instructor = currentInstructorService.resolveCurrentInstructor();
        return ClassSessionResponse.from(attendanceService.getClassSessionProgramSection(id, instructor.getId(), date));
    }

    @GetMapping("/course-sections/{id}/class-sessions/{date}")
    @PreAuthorize("hasRole('DOCENTE')")
    public ClassSessionResponse classSessionCourseSection(@PathVariable Long id, @PathVariable LocalDate date) {
        Instructor instructor = currentInstructorService.resolveCurrentInstructor();
        return ClassSessionResponse.from(attendanceService.getClassSessionCourseSection(id, instructor.getId(), date));
    }

    @PostMapping("/program-sections/{id}/class-sessions/{date}/cancel")
    @PreAuthorize("hasRole('DOCENTE')")
    public void cancelClassForProgramSection(@PathVariable Long id, @PathVariable LocalDate date) {
        Instructor instructor = currentInstructorService.resolveCurrentInstructor();
        attendanceService.cancelClassForProgramSection(id, instructor.getId(), date);
    }

    @PostMapping("/course-sections/{id}/class-sessions/{date}/cancel")
    @PreAuthorize("hasRole('DOCENTE')")
    public void cancelClassForCourseSection(@PathVariable Long id, @PathVariable LocalDate date) {
        Instructor instructor = currentInstructorService.resolveCurrentInstructor();
        attendanceService.cancelClassForCourseSection(id, instructor.getId(), date);
    }

    @GetMapping("/program-sections/{id}/attendance/history")
    @PreAuthorize("hasRole('DOCENTE') or hasRole('PRECEPTOR') or hasRole('ADMINISTRATIVO') or hasRole('DIRECTOR')")
    public AttendanceHistoryResponse historyProgramSection(@PathVariable Long id) {
        return attendanceService.getHistoryProgramSection(id, resolveInstructorIdIfTeacher());
    }

    @GetMapping("/course-sections/{id}/attendance/history")
    @PreAuthorize("hasRole('DOCENTE') or hasRole('PRECEPTOR') or hasRole('ADMINISTRATIVO') or hasRole('DIRECTOR')")
    public AttendanceHistoryResponse historyCourseSection(@PathVariable Long id) {
        return attendanceService.getHistoryCourseSection(id, resolveInstructorIdIfTeacher());
    }

    @GetMapping("/program-sections/{id}/attendance/absence-report")
    @PreAuthorize("hasRole('DOCENTE') or hasRole('PRECEPTOR') or hasRole('ADMINISTRATIVO') or hasRole('DIRECTOR')")
    public List<RosterEntryResponse> absenceReportProgramSection(
            @PathVariable Long id, @RequestParam(required = false) Integer minAbsences) {
        return attendanceService.absenceReportProgramSection(id, resolveInstructorIdIfTeacher(), minAbsences);
    }

    @GetMapping("/course-sections/{id}/attendance/absence-report")
    @PreAuthorize("hasRole('DOCENTE') or hasRole('PRECEPTOR') or hasRole('ADMINISTRATIVO') or hasRole('DIRECTOR')")
    public List<RosterEntryResponse> absenceReportCourseSection(
            @PathVariable Long id, @RequestParam(required = false) Integer minAbsences) {
        return attendanceService.absenceReportCourseSection(id, resolveInstructorIdIfTeacher(), minAbsences);
    }

    @GetMapping("/program-sections/{id}/attendance/export")
    @PreAuthorize("hasRole('DOCENTE') or hasRole('PRECEPTOR') or hasRole('ADMINISTRATIVO') or hasRole('DIRECTOR')")
    public ResponseEntity<byte[]> exportProgramSection(
            @PathVariable Long id, @RequestParam String format, @RequestParam String scope,
            @RequestParam(required = false) LocalDate classDate) {
        return exportar(attendanceExportService.exportProgramSection(id, resolveInstructorIdIfTeacher(), scope, format, classDate));
    }

    @GetMapping("/course-sections/{id}/attendance/export")
    @PreAuthorize("hasRole('DOCENTE') or hasRole('PRECEPTOR') or hasRole('ADMINISTRATIVO') or hasRole('DIRECTOR')")
    public ResponseEntity<byte[]> exportCourseSection(
            @PathVariable Long id, @RequestParam String format, @RequestParam String scope,
            @RequestParam(required = false) LocalDate classDate) {
        return exportar(attendanceExportService.exportCourseSection(id, resolveInstructorIdIfTeacher(), scope, format, classDate));
    }

    private ResponseEntity<byte[]> exportar(AttendanceExportService.ExportedFile file) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + file.filename() + "\"")
                .body(file.content());
    }

    private Long resolveInstructorIdIfTeacher() {
        boolean esDocente = SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_DOCENTE"));
        return esDocente ? currentInstructorService.resolveCurrentInstructor().getId() : null;
    }
}
