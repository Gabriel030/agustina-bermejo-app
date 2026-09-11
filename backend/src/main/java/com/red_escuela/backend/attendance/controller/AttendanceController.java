package com.red_escuela.backend.attendance.controller;

import com.red_escuela.backend.academicstructure.entity.Instructor;
import com.red_escuela.backend.attendance.dto.AttendanceHistoryResponse;
import com.red_escuela.backend.attendance.dto.AttendanceResponse;
import com.red_escuela.backend.attendance.dto.AttendanceRiskResponse;
import com.red_escuela.backend.attendance.dto.CargarAsistenciaRequest;
import com.red_escuela.backend.attendance.dto.ClassSessionResponse;
import com.red_escuela.backend.attendance.dto.JustificarAsistenciaRequest;
import com.red_escuela.backend.attendance.dto.RosterEntryResponse;
import com.red_escuela.backend.attendance.service.AttendanceExportService;
import com.red_escuela.backend.attendance.usecase.attendance.ApproveAttendanceJustificationUseCase;
import com.red_escuela.backend.attendance.usecase.attendance.CalculateCourseEnrollmentAttendanceRiskUseCase;
import com.red_escuela.backend.attendance.usecase.attendance.CalculateProgramEnrollmentAttendanceRiskUseCase;
import com.red_escuela.backend.attendance.usecase.attendance.CancelCourseSectionClassSessionCommand;
import com.red_escuela.backend.attendance.usecase.attendance.CancelCourseSectionClassSessionUseCase;
import com.red_escuela.backend.attendance.usecase.attendance.CancelProgramSectionClassSessionCommand;
import com.red_escuela.backend.attendance.usecase.attendance.CancelProgramSectionClassSessionUseCase;
import com.red_escuela.backend.attendance.usecase.attendance.GetCourseSectionAbsenceReportCommand;
import com.red_escuela.backend.attendance.usecase.attendance.GetCourseSectionAbsenceReportUseCase;
import com.red_escuela.backend.attendance.usecase.attendance.GetCourseSectionAttendanceHistoryCommand;
import com.red_escuela.backend.attendance.usecase.attendance.GetCourseSectionAttendanceHistoryUseCase;
import com.red_escuela.backend.attendance.usecase.attendance.GetCourseSectionClassSessionCommand;
import com.red_escuela.backend.attendance.usecase.attendance.GetCourseSectionClassSessionUseCase;
import com.red_escuela.backend.attendance.usecase.attendance.GetCourseSectionRosterCommand;
import com.red_escuela.backend.attendance.usecase.attendance.GetCourseSectionRosterUseCase;
import com.red_escuela.backend.attendance.usecase.attendance.GetProgramSectionAbsenceReportCommand;
import com.red_escuela.backend.attendance.usecase.attendance.GetProgramSectionAbsenceReportUseCase;
import com.red_escuela.backend.attendance.usecase.attendance.GetProgramSectionAttendanceHistoryCommand;
import com.red_escuela.backend.attendance.usecase.attendance.GetProgramSectionAttendanceHistoryUseCase;
import com.red_escuela.backend.attendance.usecase.attendance.GetProgramSectionClassSessionCommand;
import com.red_escuela.backend.attendance.usecase.attendance.GetProgramSectionClassSessionUseCase;
import com.red_escuela.backend.attendance.usecase.attendance.GetProgramSectionRosterCommand;
import com.red_escuela.backend.attendance.usecase.attendance.GetProgramSectionRosterUseCase;
import com.red_escuela.backend.attendance.usecase.attendance.JustifyAttendanceCommand;
import com.red_escuela.backend.attendance.usecase.attendance.JustifyAttendanceUseCase;
import com.red_escuela.backend.attendance.usecase.attendance.ListCourseEnrollmentAttendanceUseCase;
import com.red_escuela.backend.attendance.usecase.attendance.ListProgramEnrollmentAttendanceUseCase;
import com.red_escuela.backend.attendance.usecase.attendance.LoadCourseSectionAttendanceCommand;
import com.red_escuela.backend.attendance.usecase.attendance.LoadCourseSectionAttendanceUseCase;
import com.red_escuela.backend.attendance.usecase.attendance.LoadProgramSectionAttendanceCommand;
import com.red_escuela.backend.attendance.usecase.attendance.LoadProgramSectionAttendanceUseCase;
import com.red_escuela.backend.attendance.usecase.attendance.RejectAttendanceJustificationUseCase;
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
 * la valida el use case (NotOwnSectionException).
 */
@RestController
public class AttendanceController {

    private final LoadProgramSectionAttendanceUseCase loadProgramSectionAttendanceUseCase;
    private final LoadCourseSectionAttendanceUseCase loadCourseSectionAttendanceUseCase;
    private final JustifyAttendanceUseCase justifyAttendanceUseCase;
    private final ApproveAttendanceJustificationUseCase approveAttendanceJustificationUseCase;
    private final RejectAttendanceJustificationUseCase rejectAttendanceJustificationUseCase;
    private final ListProgramEnrollmentAttendanceUseCase listProgramEnrollmentAttendanceUseCase;
    private final ListCourseEnrollmentAttendanceUseCase listCourseEnrollmentAttendanceUseCase;
    private final CalculateProgramEnrollmentAttendanceRiskUseCase calculateProgramEnrollmentAttendanceRiskUseCase;
    private final CalculateCourseEnrollmentAttendanceRiskUseCase calculateCourseEnrollmentAttendanceRiskUseCase;
    private final GetProgramSectionRosterUseCase getProgramSectionRosterUseCase;
    private final GetCourseSectionRosterUseCase getCourseSectionRosterUseCase;
    private final GetProgramSectionClassSessionUseCase getProgramSectionClassSessionUseCase;
    private final GetCourseSectionClassSessionUseCase getCourseSectionClassSessionUseCase;
    private final CancelProgramSectionClassSessionUseCase cancelProgramSectionClassSessionUseCase;
    private final CancelCourseSectionClassSessionUseCase cancelCourseSectionClassSessionUseCase;
    private final GetProgramSectionAttendanceHistoryUseCase getProgramSectionAttendanceHistoryUseCase;
    private final GetCourseSectionAttendanceHistoryUseCase getCourseSectionAttendanceHistoryUseCase;
    private final GetProgramSectionAbsenceReportUseCase getProgramSectionAbsenceReportUseCase;
    private final GetCourseSectionAbsenceReportUseCase getCourseSectionAbsenceReportUseCase;
    private final AttendanceExportService attendanceExportService;
    private final CurrentInstructorService currentInstructorService;

    public AttendanceController(
            LoadProgramSectionAttendanceUseCase loadProgramSectionAttendanceUseCase,
            LoadCourseSectionAttendanceUseCase loadCourseSectionAttendanceUseCase,
            JustifyAttendanceUseCase justifyAttendanceUseCase,
            ApproveAttendanceJustificationUseCase approveAttendanceJustificationUseCase,
            RejectAttendanceJustificationUseCase rejectAttendanceJustificationUseCase,
            ListProgramEnrollmentAttendanceUseCase listProgramEnrollmentAttendanceUseCase,
            ListCourseEnrollmentAttendanceUseCase listCourseEnrollmentAttendanceUseCase,
            CalculateProgramEnrollmentAttendanceRiskUseCase calculateProgramEnrollmentAttendanceRiskUseCase,
            CalculateCourseEnrollmentAttendanceRiskUseCase calculateCourseEnrollmentAttendanceRiskUseCase,
            GetProgramSectionRosterUseCase getProgramSectionRosterUseCase,
            GetCourseSectionRosterUseCase getCourseSectionRosterUseCase,
            GetProgramSectionClassSessionUseCase getProgramSectionClassSessionUseCase,
            GetCourseSectionClassSessionUseCase getCourseSectionClassSessionUseCase,
            CancelProgramSectionClassSessionUseCase cancelProgramSectionClassSessionUseCase,
            CancelCourseSectionClassSessionUseCase cancelCourseSectionClassSessionUseCase,
            GetProgramSectionAttendanceHistoryUseCase getProgramSectionAttendanceHistoryUseCase,
            GetCourseSectionAttendanceHistoryUseCase getCourseSectionAttendanceHistoryUseCase,
            GetProgramSectionAbsenceReportUseCase getProgramSectionAbsenceReportUseCase,
            GetCourseSectionAbsenceReportUseCase getCourseSectionAbsenceReportUseCase,
            AttendanceExportService attendanceExportService,
            CurrentInstructorService currentInstructorService) {
        this.loadProgramSectionAttendanceUseCase = loadProgramSectionAttendanceUseCase;
        this.loadCourseSectionAttendanceUseCase = loadCourseSectionAttendanceUseCase;
        this.justifyAttendanceUseCase = justifyAttendanceUseCase;
        this.approveAttendanceJustificationUseCase = approveAttendanceJustificationUseCase;
        this.rejectAttendanceJustificationUseCase = rejectAttendanceJustificationUseCase;
        this.listProgramEnrollmentAttendanceUseCase = listProgramEnrollmentAttendanceUseCase;
        this.listCourseEnrollmentAttendanceUseCase = listCourseEnrollmentAttendanceUseCase;
        this.calculateProgramEnrollmentAttendanceRiskUseCase = calculateProgramEnrollmentAttendanceRiskUseCase;
        this.calculateCourseEnrollmentAttendanceRiskUseCase = calculateCourseEnrollmentAttendanceRiskUseCase;
        this.getProgramSectionRosterUseCase = getProgramSectionRosterUseCase;
        this.getCourseSectionRosterUseCase = getCourseSectionRosterUseCase;
        this.getProgramSectionClassSessionUseCase = getProgramSectionClassSessionUseCase;
        this.getCourseSectionClassSessionUseCase = getCourseSectionClassSessionUseCase;
        this.cancelProgramSectionClassSessionUseCase = cancelProgramSectionClassSessionUseCase;
        this.cancelCourseSectionClassSessionUseCase = cancelCourseSectionClassSessionUseCase;
        this.getProgramSectionAttendanceHistoryUseCase = getProgramSectionAttendanceHistoryUseCase;
        this.getCourseSectionAttendanceHistoryUseCase = getCourseSectionAttendanceHistoryUseCase;
        this.getProgramSectionAbsenceReportUseCase = getProgramSectionAbsenceReportUseCase;
        this.getCourseSectionAbsenceReportUseCase = getCourseSectionAbsenceReportUseCase;
        this.attendanceExportService = attendanceExportService;
        this.currentInstructorService = currentInstructorService;
    }

    @PostMapping("/program-sections/{id}/attendance")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('DOCENTE')")
    public List<AttendanceResponse> cargarAsistenciaProgramSection(
            @PathVariable Long id, @Valid @RequestBody CargarAsistenciaRequest request) {
        Instructor instructor = currentInstructorService.resolveCurrentInstructor();
        return loadProgramSectionAttendanceUseCase.load(new LoadProgramSectionAttendanceCommand(id, instructor.getId(), request))
                .stream().map(AttendanceResponse::from).toList();
    }

    @PostMapping("/course-sections/{id}/attendance")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('DOCENTE')")
    public List<AttendanceResponse> cargarAsistenciaCourseSection(
            @PathVariable Long id, @Valid @RequestBody CargarAsistenciaRequest request) {
        Instructor instructor = currentInstructorService.resolveCurrentInstructor();
        return loadCourseSectionAttendanceUseCase.load(new LoadCourseSectionAttendanceCommand(id, instructor.getId(), request))
                .stream().map(AttendanceResponse::from).toList();
    }

    @PostMapping("/attendance/{id}/justify")
    @PreAuthorize("hasRole('DOCENTE') or hasRole('PRECEPTOR') or hasRole('ADMINISTRATIVO')")
    public AttendanceResponse justificar(@PathVariable Long id, @Valid @RequestBody JustificarAsistenciaRequest request) {
        return AttendanceResponse.from(
                justifyAttendanceUseCase.justify(new JustifyAttendanceCommand(id, request.reason(), request.attachmentUrl())));
    }

    @PostMapping("/attendance/{id}/approve-justification")
    @PreAuthorize("hasRole('PRECEPTOR') or hasRole('ADMINISTRATIVO')")
    public AttendanceResponse aprobarJustificacion(@PathVariable Long id) {
        return AttendanceResponse.from(approveAttendanceJustificationUseCase.approve(id));
    }

    @PostMapping("/attendance/{id}/reject-justification")
    @PreAuthorize("hasRole('PRECEPTOR') or hasRole('ADMINISTRATIVO')")
    public AttendanceResponse rechazarJustificacion(@PathVariable Long id) {
        return AttendanceResponse.from(rejectAttendanceJustificationUseCase.reject(id));
    }

    @GetMapping("/section-enrollments/{id}/attendance")
    @PreAuthorize("hasRole('PRECEPTOR') or hasRole('ADMINISTRATIVO') or hasRole('DIRECTOR')")
    public List<AttendanceResponse> listarAsistenciaProgramEnrollment(@PathVariable Long id) {
        return listProgramEnrollmentAttendanceUseCase.list(id).stream().map(AttendanceResponse::from).toList();
    }

    @GetMapping("/course-section-enrollments/{id}/attendance")
    @PreAuthorize("hasRole('PRECEPTOR') or hasRole('ADMINISTRATIVO') or hasRole('DIRECTOR')")
    public List<AttendanceResponse> listarAsistenciaCourseEnrollment(@PathVariable Long id) {
        return listCourseEnrollmentAttendanceUseCase.list(id).stream().map(AttendanceResponse::from).toList();
    }

    @GetMapping("/section-enrollments/{id}/attendance/risk")
    @PreAuthorize("hasRole('PRECEPTOR') or hasRole('ADMINISTRATIVO') or hasRole('DIRECTOR')")
    public AttendanceRiskResponse riesgoProgramEnrollment(@PathVariable Long id) {
        return calculateProgramEnrollmentAttendanceRiskUseCase.calculate(id);
    }

    @GetMapping("/course-section-enrollments/{id}/attendance/risk")
    @PreAuthorize("hasRole('PRECEPTOR') or hasRole('ADMINISTRATIVO') or hasRole('DIRECTOR')")
    public AttendanceRiskResponse riesgoCourseEnrollment(@PathVariable Long id) {
        return calculateCourseEnrollmentAttendanceRiskUseCase.calculate(id);
    }

    @GetMapping("/program-sections/{id}/roster")
    @PreAuthorize("hasRole('DOCENTE')")
    public List<RosterEntryResponse> rosterProgramSection(@PathVariable Long id) {
        Instructor instructor = currentInstructorService.resolveCurrentInstructor();
        return getProgramSectionRosterUseCase.get(new GetProgramSectionRosterCommand(id, instructor.getId()));
    }

    @GetMapping("/course-sections/{id}/roster")
    @PreAuthorize("hasRole('DOCENTE')")
    public List<RosterEntryResponse> rosterCourseSection(@PathVariable Long id) {
        Instructor instructor = currentInstructorService.resolveCurrentInstructor();
        return getCourseSectionRosterUseCase.get(new GetCourseSectionRosterCommand(id, instructor.getId()));
    }

    @GetMapping("/program-sections/{id}/class-sessions/{date}")
    @PreAuthorize("hasRole('DOCENTE')")
    public ClassSessionResponse claseProgramSection(@PathVariable Long id, @PathVariable LocalDate date) {
        Instructor instructor = currentInstructorService.resolveCurrentInstructor();
        return ClassSessionResponse.from(
                getProgramSectionClassSessionUseCase.get(new GetProgramSectionClassSessionCommand(id, instructor.getId(), date)));
    }

    @GetMapping("/course-sections/{id}/class-sessions/{date}")
    @PreAuthorize("hasRole('DOCENTE')")
    public ClassSessionResponse claseCourseSection(@PathVariable Long id, @PathVariable LocalDate date) {
        Instructor instructor = currentInstructorService.resolveCurrentInstructor();
        return ClassSessionResponse.from(
                getCourseSectionClassSessionUseCase.get(new GetCourseSectionClassSessionCommand(id, instructor.getId(), date)));
    }

    @PostMapping("/program-sections/{id}/class-sessions/{date}/cancel")
    @PreAuthorize("hasRole('DOCENTE')")
    public void cancelarClaseProgramSection(@PathVariable Long id, @PathVariable LocalDate date) {
        Instructor instructor = currentInstructorService.resolveCurrentInstructor();
        cancelProgramSectionClassSessionUseCase.cancel(new CancelProgramSectionClassSessionCommand(id, instructor.getId(), date));
    }

    @PostMapping("/course-sections/{id}/class-sessions/{date}/cancel")
    @PreAuthorize("hasRole('DOCENTE')")
    public void cancelarClaseCourseSection(@PathVariable Long id, @PathVariable LocalDate date) {
        Instructor instructor = currentInstructorService.resolveCurrentInstructor();
        cancelCourseSectionClassSessionUseCase.cancel(new CancelCourseSectionClassSessionCommand(id, instructor.getId(), date));
    }

    @GetMapping("/program-sections/{id}/attendance/history")
    @PreAuthorize("hasRole('DOCENTE') or hasRole('PRECEPTOR') or hasRole('ADMINISTRATIVO') or hasRole('DIRECTOR')")
    public AttendanceHistoryResponse historialProgramSection(@PathVariable Long id) {
        return getProgramSectionAttendanceHistoryUseCase.get(
                new GetProgramSectionAttendanceHistoryCommand(id, resolveInstructorIdSiEsDocente()));
    }

    @GetMapping("/course-sections/{id}/attendance/history")
    @PreAuthorize("hasRole('DOCENTE') or hasRole('PRECEPTOR') or hasRole('ADMINISTRATIVO') or hasRole('DIRECTOR')")
    public AttendanceHistoryResponse historialCourseSection(@PathVariable Long id) {
        return getCourseSectionAttendanceHistoryUseCase.get(
                new GetCourseSectionAttendanceHistoryCommand(id, resolveInstructorIdSiEsDocente()));
    }

    @GetMapping("/program-sections/{id}/attendance/absence-report")
    @PreAuthorize("hasRole('DOCENTE') or hasRole('PRECEPTOR') or hasRole('ADMINISTRATIVO') or hasRole('DIRECTOR')")
    public List<RosterEntryResponse> reporteDeAusenciasProgramSection(
            @PathVariable Long id, @RequestParam(required = false) Integer minAbsences) {
        return getProgramSectionAbsenceReportUseCase.get(
                new GetProgramSectionAbsenceReportCommand(id, resolveInstructorIdSiEsDocente(), minAbsences));
    }

    @GetMapping("/course-sections/{id}/attendance/absence-report")
    @PreAuthorize("hasRole('DOCENTE') or hasRole('PRECEPTOR') or hasRole('ADMINISTRATIVO') or hasRole('DIRECTOR')")
    public List<RosterEntryResponse> reporteDeAusenciasCourseSection(
            @PathVariable Long id, @RequestParam(required = false) Integer minAbsences) {
        return getCourseSectionAbsenceReportUseCase.get(
                new GetCourseSectionAbsenceReportCommand(id, resolveInstructorIdSiEsDocente(), minAbsences));
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
