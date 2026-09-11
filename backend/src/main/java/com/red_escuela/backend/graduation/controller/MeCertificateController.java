package com.red_escuela.backend.graduation.controller;

import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.enrollment.exception.ProgramEnrollmentNotFoundException;
import com.red_escuela.backend.graduation.service.CertificateService;
import com.red_escuela.backend.identity.service.CurrentStudentService;
import com.red_escuela.backend.enrollment.service.ProgramEnrollmentService;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * RF-AL-12: emisión de constancias, autoservicio del alumno. Sin entidad propia (decisión 6):
 * el PDF se arma on-demand y se devuelve directo en la respuesta HTTP.
 */
@RestController
@RequestMapping("/me/program-enrollments/{id}/certificates")
public class MeCertificateController {

    private final CurrentStudentService currentStudentService;
    private final ProgramEnrollmentService programEnrollmentService;
    private final CertificateService certificateService;

    public MeCertificateController(
            CurrentStudentService currentStudentService, ProgramEnrollmentService programEnrollmentService,
            CertificateService certificateService) {
        this.currentStudentService = currentStudentService;
        this.programEnrollmentService = programEnrollmentService;
        this.certificateService = certificateService;
    }

    @GetMapping("/regular")
    public ResponseEntity<byte[]> constanciaRegular(@PathVariable Long id) {
        exigirPropietario(id);
        return pdfResponse(certificateService.emitirConstanciaRegular(id), "constancia-regular.pdf");
    }

    @GetMapping("/approved-subjects")
    public ResponseEntity<byte[]> constanciaMateriasAprobadas(@PathVariable Long id) {
        exigirPropietario(id);
        return pdfResponse(certificateService.emitirConstanciaMateriasAprobadas(id), "constancia-materias-aprobadas.pdf");
    }

    // 404 (no 403) si la ProgramEnrollment no es del alumno autenticado — mismo criterio que
    // MeEnrollmentController.exigirPropietario.
    private void exigirPropietario(Long programEnrollmentId) {
        Student student = currentStudentService.resolveCurrentStudent();
        ProgramEnrollment enrollment = programEnrollmentService.verDetalle(programEnrollmentId);
        if (!enrollment.getStudent().getId().equals(student.getId())) {
            throw new ProgramEnrollmentNotFoundException(programEnrollmentId);
        }
    }

    private ResponseEntity<byte[]> pdfResponse(byte[] pdf, String filename) {
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(filename).build().toString())
                .body(pdf);
    }
}
