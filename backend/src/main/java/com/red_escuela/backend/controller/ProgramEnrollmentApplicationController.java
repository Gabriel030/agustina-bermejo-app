package com.red_escuela.backend.controller;

import com.red_escuela.backend.dto.CreateProgramEnrollmentApplicationRequest;
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

    @PostMapping("/me/program-enrollment-applications")
    @ResponseStatus(HttpStatus.CREATED)
    public ProgramEnrollmentApplicationResponse solicitar(@Valid @RequestBody CreateProgramEnrollmentApplicationRequest request) {
        Student student = currentStudentService.resolveCurrentStudent();
        ProgramEnrollmentApplication application = applicationService.solicitar(student, request.programId());
        return ProgramEnrollmentApplicationResponse.from(application);
    }

    @GetMapping("/me/program-enrollment-applications")
    public List<ProgramEnrollmentApplicationResponse> misSolicitudes() {
        Student student = currentStudentService.resolveCurrentStudent();
        return applicationService.listarPropias(student.getId()).stream()
                .map(ProgramEnrollmentApplicationResponse::from).toList();
    }

    @PostMapping("/me/program-enrollment-applications/{id}/documents")
    @ResponseStatus(HttpStatus.CREATED)
    public void agregarDocumento(@PathVariable Long id, @RequestParam DocumentType type, @RequestPart MultipartFile file) {
        Student student = currentStudentService.resolveCurrentStudent();
        ProgramEnrollmentApplication application = applicationService.verDetalle(id);
        exigirPropietario(application, student);
        applicationService.agregarDocumento(id, type, file);
    }

    @GetMapping("/program-enrollment-applications")
    @PreAuthorize("hasRole('PRECEPTOR') or hasRole('ADMINISTRATIVO')")
    public List<ProgramEnrollmentApplicationResponse> listarPendientes() {
        return applicationService.listarPendientes().stream()
                .map(ProgramEnrollmentApplicationResponse::from).toList();
    }

    @PostMapping("/program-enrollment-applications/{id}/approve")
    @PreAuthorize("hasRole('PRECEPTOR') or hasRole('ADMINISTRATIVO')")
    public ProgramEnrollmentApplicationResponse aprobar(@PathVariable Long id) {
        Person decidedBy = currentPersonService.resolveCurrentPerson();
        return ProgramEnrollmentApplicationResponse.from(applicationService.aprobar(id, decidedBy));
    }

    @PostMapping("/program-enrollment-applications/{id}/reject")
    @PreAuthorize("hasRole('PRECEPTOR') or hasRole('ADMINISTRATIVO')")
    public ProgramEnrollmentApplicationResponse rechazar(
            @PathVariable Long id, @Valid @RequestBody RejectProgramEnrollmentApplicationRequest request) {
        Person decidedBy = currentPersonService.resolveCurrentPerson();
        return ProgramEnrollmentApplicationResponse.from(applicationService.rechazar(id, decidedBy, request.reason()));
    }

    // Mismo patrón que MeEnrollmentController.exigirPropietario: 404 (no 403) para
    // no revelar que una solicitud ajena existe.
    private void exigirPropietario(ProgramEnrollmentApplication application, Student student) {
        if (!application.getStudent().getId().equals(student.getId())) {
            throw new ProgramEnrollmentApplicationNotFoundException(application.getId());
        }
    }
}
