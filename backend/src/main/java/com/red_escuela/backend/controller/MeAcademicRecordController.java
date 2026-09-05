package com.red_escuela.backend.controller;

import com.red_escuela.backend.dto.AttendanceResponse;
import com.red_escuela.backend.dto.AttendanceRiskResponse;
import com.red_escuela.backend.dto.GradeItemResponse;
import com.red_escuela.backend.entity.Attendance;
import com.red_escuela.backend.entity.SubjectSectionEnrollment;
import com.red_escuela.backend.entity.Student;
import com.red_escuela.backend.exception.AttendanceNotFoundException;
import com.red_escuela.backend.exception.SectionEnrollmentNotFoundException;
import com.red_escuela.backend.exception.UnsupportedFileTypeException;
import com.red_escuela.backend.service.AttendanceService;
import com.red_escuela.backend.service.CurrentStudentService;
import com.red_escuela.backend.service.FileStorageService;
import com.red_escuela.backend.service.GradeItemService;
import com.red_escuela.backend.service.SubjectSectionEnrollmentService;
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
    private final AttendanceService attendanceService;
    private final GradeItemService gradeItemService;
    private final FileStorageService fileStorageService;

    public MeAcademicRecordController(
            CurrentStudentService currentStudentService,
            SubjectSectionEnrollmentService subjectSectionEnrollmentService,
            AttendanceService attendanceService,
            GradeItemService gradeItemService,
            FileStorageService fileStorageService) {
        this.currentStudentService = currentStudentService;
        this.subjectSectionEnrollmentService = subjectSectionEnrollmentService;
        this.attendanceService = attendanceService;
        this.gradeItemService = gradeItemService;
        this.fileStorageService = fileStorageService;
    }

    @GetMapping("/section-enrollments/{id}/attendance")
    public List<AttendanceResponse> myAttendance(@PathVariable Long id) {
        requireOwner(id);
        return attendanceService.listByProgramEnrollment(id).stream().map(AttendanceResponse::from).toList();
    }

    // % de ausentismo real y si eso ya supera el máximo permitido de la comisión
    // (SubjectSection.maxAbsencePercentage, política institucional real — nunca un
    // 70% fijo hardcodeado) — reusa el mismo cálculo que ya usa Preceptoría
    // (AttendanceService.calculateDropoutRiskByProgramEnrollment), autoservicio.
    @GetMapping("/section-enrollments/{id}/attendance/risk")
    public AttendanceRiskResponse myAttendanceRisk(@PathVariable Long id) {
        requireOwner(id);
        return attendanceService.calculateDropoutRiskByProgramEnrollment(id);
    }

    @GetMapping("/section-enrollments/{id}/grade-items")
    public List<GradeItemResponse> myGrades(@PathVariable Long id) {
        requireOwner(id);
        return gradeItemService.listByProgramEnrollment(id).stream().map(GradeItemResponse::from).toList();
    }

    @PostMapping(value = "/attendance/{id}/justify", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public AttendanceResponse justificarMiInasistencia(
            @PathVariable Long id, @RequestParam(required = false) String reason, @RequestPart MultipartFile proofFile) {
        Student student = currentStudentService.resolveCurrentStudent();
        Attendance attendance = attendanceService.viewDetail(id);
        requireAttendanceOwner(attendance, student);
        String contentType = proofFile.getContentType();
        boolean allowedType = MediaType.APPLICATION_PDF_VALUE.equals(contentType) || MediaType.IMAGE_JPEG_VALUE.equals(contentType);
        if (!allowedType) {
            throw new UnsupportedFileTypeException(contentType);
        }
        String relativePath = fileStorageService.store(student.getId(), "justificaciones-asistencia", proofFile);
        String justificationReason = reason != null && !reason.isBlank() ? reason : "Comprobante subido por el alumno";
        return AttendanceResponse.from(attendanceService.uploadJustificationProof(id, justificationReason, relativePath));
    }

    // 404 (no 403) si la cursada no es del alumno autenticado — mismo criterio que
    // MeEnrollmentController.exigirPropietario.
    private void requireOwner(Long enrollmentId) {
        Student student = currentStudentService.resolveCurrentStudent();
        SubjectSectionEnrollment enrollment = subjectSectionEnrollmentService.viewDetail(enrollmentId);
        if (!enrollment.getStudent().getId().equals(student.getId())) {
            throw new SectionEnrollmentNotFoundException(enrollmentId);
        }
    }

    private void requireAttendanceOwner(Attendance attendance, Student student) {
        SubjectSectionEnrollment enrollment = attendance.getProgramSubjectSectionEnrollment();
        if (enrollment == null || !enrollment.getStudent().getId().equals(student.getId())) {
            throw new AttendanceNotFoundException(attendance.getId());
        }
    }
}
