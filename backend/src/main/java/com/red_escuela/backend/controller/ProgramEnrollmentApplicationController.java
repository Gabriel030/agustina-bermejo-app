package com.red_escuela.backend.controller;

import com.red_escuela.backend.dto.ProgramEnrollmentApplicationResponse;
import com.red_escuela.backend.dto.RejectProgramEnrollmentApplicationRequest;
import com.red_escuela.backend.entity.DocumentType;
import com.red_escuela.backend.entity.Person;
import com.red_escuela.backend.entity.ProgramEnrollmentApplication;
import com.red_escuela.backend.entity.Student;
import com.red_escuela.backend.exception.ProgramEnrollmentApplicationNotFoundException;
import com.red_escuela.backend.service.CurrentPersonService;
import com.red_escuela.backend.service.CurrentStudentService;
import com.red_escuela.backend.service.ProgramEnrollmentApplicationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * Autoservicio (/me/...) sin @PreAuthorize adicional — mismo criterio que
 * MeEnrollmentController, la autenticación ya la exige el filtro JWT global.
 * Los endpoints de decisión (approve/reject) sí llevan @PreAuthorize — mismo
 * rol que ya confirma prospectos (ProspectController).
 */
@RestController
public class ProgramEnrollmentApplicationController {

    private final CurrentStudentService currentStudentService;
    private final CurrentPersonService currentPersonService;
    private final ProgramEnrollmentApplicationService applicationService;

    public ProgramEnrollmentApplicationController(
            CurrentStudentService currentStudentService,
            CurrentPersonService currentPersonService,
            ProgramEnrollmentApplicationService applicationService) {
        this.currentStudentService = currentStudentService;
        this.currentPersonService = currentPersonService;
        this.applicationService = applicationService;
    }

    // Multipart desde la Fase 3: el pago de la matrícula es un paso bloqueante de
    // la misma request (ver spec técnica) — ya no hay un request() sin comprobante.
    @PostMapping(value = "/me/program-enrollment-applications", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ProgramEnrollmentApplicationResponse request(
            @RequestParam Long programId, @RequestParam Long cohortId, @RequestPart MultipartFile receiptFile) {
        Student student = currentStudentService.resolveCurrentStudent();
        ProgramEnrollmentApplication application = applicationService.requestWithPayment(student, programId, cohortId, receiptFile);
        return ProgramEnrollmentApplicationResponse.from(application);
    }

    @GetMapping("/me/program-enrollment-applications")
    public List<ProgramEnrollmentApplicationResponse> myProgramEnrollmentApplications() {
        Student student = currentStudentService.resolveCurrentStudent();
        return applicationService.listOwn(student.getId()).stream()
                .map(ProgramEnrollmentApplicationResponse::from).toList();
    }

    @PostMapping("/me/program-enrollment-applications/{id}/documents")
    @ResponseStatus(HttpStatus.CREATED)
    public void addDocument(@PathVariable Long id, @RequestParam DocumentType type, @RequestPart MultipartFile file) {
        Student student = currentStudentService.resolveCurrentStudent();
        ProgramEnrollmentApplication application = applicationService.viewDetail(id);
        requireOwner(application, student);
        applicationService.addDocument(id, type, file);
    }

    @GetMapping("/program-enrollment-applications")
    @PreAuthorize("hasRole('PRECEPTOR') or hasRole('ADMINISTRATIVO')")
    public List<ProgramEnrollmentApplicationResponse> listPending() {
        return applicationService.listPending().stream()
                .map(ProgramEnrollmentApplicationResponse::from).toList();
    }

    @PostMapping("/program-enrollment-applications/{id}/approve")
    @PreAuthorize("hasRole('PRECEPTOR') or hasRole('ADMINISTRATIVO')")
    public ProgramEnrollmentApplicationResponse approve(@PathVariable Long id) {
        Person decidedBy = currentPersonService.resolveCurrentPerson();
        return ProgramEnrollmentApplicationResponse.from(applicationService.approve(id, decidedBy));
    }

    @PostMapping("/program-enrollment-applications/{id}/reject")
    @PreAuthorize("hasRole('PRECEPTOR') or hasRole('ADMINISTRATIVO')")
    public ProgramEnrollmentApplicationResponse reject(
            @PathVariable Long id, @Valid @RequestBody RejectProgramEnrollmentApplicationRequest request) {
        Person decidedBy = currentPersonService.resolveCurrentPerson();
        return ProgramEnrollmentApplicationResponse.from(applicationService.reject(id, decidedBy, request.reason()));
    }

    // Mismo patrón que MeEnrollmentController.exigirPropietario: 404 (no 403) para
    // no revelar que una solicitud ajena existe.
    private void requireOwner(ProgramEnrollmentApplication application, Student student) {
        if (!application.getStudent().getId().equals(student.getId())) {
            throw new ProgramEnrollmentApplicationNotFoundException(application.getId());
        }
    }
}
