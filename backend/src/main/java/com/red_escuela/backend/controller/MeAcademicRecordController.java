package com.red_escuela.backend.controller;

import com.red_escuela.backend.dto.AttendanceResponse;
import com.red_escuela.backend.dto.GradeItemResponse;
import com.red_escuela.backend.entity.SubjectSectionEnrollment;
import com.red_escuela.backend.entity.Student;
import com.red_escuela.backend.exception.SectionEnrollmentNotFoundException;
import com.red_escuela.backend.service.AttendanceService;
import com.red_escuela.backend.service.CurrentStudentService;
import com.red_escuela.backend.service.GradeItemService;
import com.red_escuela.backend.service.SubjectSectionEnrollmentService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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

    public MeAcademicRecordController(
            CurrentStudentService currentStudentService,
            SubjectSectionEnrollmentService subjectSectionEnrollmentService,
            AttendanceService attendanceService,
            GradeItemService gradeItemService) {
        this.currentStudentService = currentStudentService;
        this.subjectSectionEnrollmentService = subjectSectionEnrollmentService;
        this.attendanceService = attendanceService;
        this.gradeItemService = gradeItemService;
    }

    @GetMapping("/section-enrollments/{id}/attendance")
    public List<AttendanceResponse> miAsistencia(@PathVariable Long id) {
        exigirPropietario(id);
        return attendanceService.listarPorProgramEnrollment(id).stream().map(AttendanceResponse::from).toList();
    }

    @GetMapping("/section-enrollments/{id}/grade-items")
    public List<GradeItemResponse> misNotas(@PathVariable Long id) {
        exigirPropietario(id);
        return gradeItemService.listarPorProgramEnrollment(id).stream().map(GradeItemResponse::from).toList();
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
}
