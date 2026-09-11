package com.red_escuela.backend.controller;

import com.red_escuela.backend.attendance.dto.AttendanceResponse;
import com.red_escuela.backend.attendance.dto.AttendanceRiskResponse;
import com.red_escuela.backend.evaluation.dto.GradeItemResponse;
import com.red_escuela.backend.attendance.entity.Attendance;
import com.red_escuela.backend.enrollment.entity.SubjectSectionEnrollment;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.attendance.exception.AttendanceNotFoundException;
import com.red_escuela.backend.enrollment.exception.SectionEnrollmentNotFoundException;
import com.red_escuela.backend.exception.UnsupportedFileTypeException;
import com.red_escuela.backend.attendance.usecase.attendance.CalculateProgramEnrollmentAttendanceRiskUseCase;
import com.red_escuela.backend.attendance.usecase.attendance.GetAttendanceUseCase;
import com.red_escuela.backend.attendance.usecase.attendance.ListProgramEnrollmentAttendanceUseCase;
import com.red_escuela.backend.attendance.usecase.attendance.SubmitAttendanceProofCommand;
import com.red_escuela.backend.attendance.usecase.attendance.SubmitAttendanceProofUseCase;
import com.red_escuela.backend.identity.service.CurrentStudentService;
import com.red_escuela.backend.service.FileStorageService;
import com.red_escuela.backend.evaluation.service.GradeItemService;
import com.red_escuela.backend.enrollment.service.SubjectSectionEnrollmentService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * RF-AL-07 extendido: autoservicio del alumno para ver sus propias notas y asistencia (mismo
 * criterio de ownership 404-no-403 que MeEnrollmentController — ver exigirPropietario allá).
 * Alcance limitado al lado "program" (SubjectSectionEnrollment), igual que el resto de los
 * endpoints /me/... existentes hoy (el lado "course" todavía no tiene autoservicio propio).
 */
@RestController
@RequestMapping("/me")
public class MeAcademicRecordController {

    private final CurrentStudentService currentStudentService;
    private final SubjectSectionEnrollmentService subjectSectionEnrollmentService;
    private final ListProgramEnrollmentAttendanceUseCase listProgramEnrollmentAttendanceUseCase;
    private final CalculateProgramEnrollmentAttendanceRiskUseCase calculateProgramEnrollmentAttendanceRiskUseCase;
    private final GetAttendanceUseCase getAttendanceUseCase;
    private final SubmitAttendanceProofUseCase submitAttendanceProofUseCase;
    private final GradeItemService gradeItemService;
    private final FileStorageService fileStorageService;

    public MeAcademicRecordController(
            CurrentStudentService currentStudentService,
            SubjectSectionEnrollmentService subjectSectionEnrollmentService,
            ListProgramEnrollmentAttendanceUseCase listProgramEnrollmentAttendanceUseCase,
            CalculateProgramEnrollmentAttendanceRiskUseCase calculateProgramEnrollmentAttendanceRiskUseCase,
            GetAttendanceUseCase getAttendanceUseCase,
            SubmitAttendanceProofUseCase submitAttendanceProofUseCase,
            GradeItemService gradeItemService,
            FileStorageService fileStorageService) {
        this.currentStudentService = currentStudentService;
        this.subjectSectionEnrollmentService = subjectSectionEnrollmentService;
        this.listProgramEnrollmentAttendanceUseCase = listProgramEnrollmentAttendanceUseCase;
        this.calculateProgramEnrollmentAttendanceRiskUseCase = calculateProgramEnrollmentAttendanceRiskUseCase;
        this.getAttendanceUseCase = getAttendanceUseCase;
        this.submitAttendanceProofUseCase = submitAttendanceProofUseCase;
        this.gradeItemService = gradeItemService;
        this.fileStorageService = fileStorageService;
    }

    @GetMapping("/section-enrollments/{id}/attendance")
    public List<AttendanceResponse> miAsistencia(@PathVariable Long id) {
        exigirPropietario(id);
        return listProgramEnrollmentAttendanceUseCase.list(id).stream().map(AttendanceResponse::from).toList();
    }

    // % de ausentismo real y si eso ya supera el máximo permitido de la comisión
    // (SubjectSection.maxAbsencePercentage, política institucional real — nunca un
    // 70% fijo hardcodeado) — reusa el mismo cálculo que ya usa Preceptoría, autoservicio.
    @GetMapping("/section-enrollments/{id}/attendance/risk")
    public AttendanceRiskResponse miRiesgoDeAsistencia(@PathVariable Long id) {
        exigirPropietario(id);
        return calculateProgramEnrollmentAttendanceRiskUseCase.calculate(id);
    }

    @GetMapping("/section-enrollments/{id}/grade-items")
    public List<GradeItemResponse> misNotas(@PathVariable Long id) {
        exigirPropietario(id);
        return gradeItemService.listarPorProgramEnrollment(id).stream().map(GradeItemResponse::from).toList();
    }

    @PostMapping(value = "/attendance/{id}/justify", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public AttendanceResponse justificarMiInasistencia(
            @PathVariable Long id, @RequestParam(required = false) String reason, @RequestPart MultipartFile comprobante) {
        Student student = currentStudentService.resolveCurrentStudent();
        Attendance attendance = getAttendanceUseCase.get(id);
        exigirPropietarioDeAsistencia(attendance, student);
        String tipo = comprobante.getContentType();
        boolean tipoPermitido = MediaType.APPLICATION_PDF_VALUE.equals(tipo) || MediaType.IMAGE_JPEG_VALUE.equals(tipo);
        if (!tipoPermitido) {
            throw new UnsupportedFileTypeException(tipo);
        }
        String relativePath = fileStorageService.store(student.getId(), "justificaciones-asistencia", comprobante);
        String motivo = reason != null && !reason.isBlank() ? reason : "Comprobante subido por el alumno";
        return AttendanceResponse.from(submitAttendanceProofUseCase.submit(new SubmitAttendanceProofCommand(id, motivo, relativePath)));
    }

    // 404 (no 403) si la cursada no es del alumno autenticado — mismo criterio que
    // MeEnrollmentController.exigirPropietario.
    private void exigirPropietario(Long enrollmentId) {
        Student student = currentStudentService.resolveCurrentStudent();
        SubjectSectionEnrollment enrollment = subjectSectionEnrollmentService.verDetalle(enrollmentId);
        if (!enrollment.getStudent().getId().equals(student.getId())) {
            throw new SectionEnrollmentNotFoundException(enrollmentId);
        }
    }

    private void exigirPropietarioDeAsistencia(Attendance attendance, Student student) {
        SubjectSectionEnrollment enrollment = attendance.getProgramSubjectSectionEnrollment();
        if (enrollment == null || !enrollment.getStudent().getId().equals(student.getId())) {
            throw new AttendanceNotFoundException(attendance.getId());
        }
    }
}
