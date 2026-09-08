package com.red_escuela.backend.controller;

import com.red_escuela.backend.billing.dto.AdvanceBillingYearRequest;
import com.red_escuela.backend.billing.dto.CreateAdHocInvoiceRequest;
import com.red_escuela.backend.billing.dto.InvoiceResponse;
import com.red_escuela.backend.billing.service.AdHocInvoiceService;
import com.red_escuela.backend.dto.CreateProgramEnrollmentRequest;
import com.red_escuela.backend.dto.ProgramEnrollmentResponse;
import com.red_escuela.backend.dto.ProgramProgressResponse;
import com.red_escuela.backend.dto.SubjectAttendanceSummaryResponse;
import com.red_escuela.backend.service.ProgramEnrollmentService;
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

@RestController
public class ProgramEnrollmentController {

    private final ProgramEnrollmentService programEnrollmentService;
    private final AdHocInvoiceService adHocInvoiceService;

    public ProgramEnrollmentController(ProgramEnrollmentService programEnrollmentService, AdHocInvoiceService adHocInvoiceService) {
        this.programEnrollmentService = programEnrollmentService;
        this.adHocInvoiceService = adHocInvoiceService;
    }

    @PostMapping("/students/{id}/program-enrollments")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('PRECEPTOR') or hasRole('ADMINISTRATIVO')")
    public ProgramEnrollmentResponse matricular(@PathVariable Long id, @Valid @RequestBody CreateProgramEnrollmentRequest request) {
        return ProgramEnrollmentResponse.from(programEnrollmentService.matricular(id, request));
    }

    @PostMapping("/program-enrollments/{id}/withdraw")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('PRECEPTOR') or hasRole('ADMINISTRATIVO')")
    public void darDeBaja(@PathVariable Long id) {
        programEnrollmentService.darDeBaja(id);
    }

    @GetMapping("/students/{id}/program-enrollments")
    @PreAuthorize("hasRole('PRECEPTOR') or hasRole('ADMINISTRATIVO') or hasRole('DIRECTOR')")
    public List<ProgramEnrollmentResponse> listarPorAlumno(@PathVariable Long id) {
        return programEnrollmentService.listarPorAlumno(id).stream().map(ProgramEnrollmentResponse::from).toList();
    }

    @GetMapping("/program-enrollments/{id}")
    @PreAuthorize("hasRole('PRECEPTOR') or hasRole('ADMINISTRATIVO') or hasRole('DIRECTOR')")
    public ProgramEnrollmentResponse verDetalle(@PathVariable Long id) {
        return ProgramEnrollmentResponse.from(programEnrollmentService.verDetalle(id));
    }

    @GetMapping("/program-enrollments/{id}/progress")
    @PreAuthorize("hasRole('PRECEPTOR') or hasRole('ADMINISTRATIVO') or hasRole('DIRECTOR')")
    public ProgramProgressResponse verProgreso(@PathVariable Long id) {
        return programEnrollmentService.calcularProgreso(id);
    }

    @GetMapping("/program-enrollments/{id}/attendance-summary")
    @PreAuthorize("hasRole('PRECEPTOR') or hasRole('ADMINISTRATIVO') or hasRole('DIRECTOR')")
    public List<SubjectAttendanceSummaryResponse> verResumenAsistencia(@PathVariable Long id) {
        return programEnrollmentService.resumenAsistenciaPorMateria(id);
    }

    // Fase 6.1 (decisión 2026-09-03): progresar de año (o repetir uno) cambia a qué plantilla
    // anual se le factura de ahí en adelante — no toca la cohorte de origen ni recobra matrícula.
    @PostMapping("/program-enrollments/{id}/advance-billing-year")
    @PreAuthorize("hasRole('PRECEPTOR') or hasRole('ADMINISTRATIVO')")
    public ProgramEnrollmentResponse avanzarAñoLectivo(@PathVariable Long id, @Valid @RequestBody AdvanceBillingYearRequest request) {
        return ProgramEnrollmentResponse.from(programEnrollmentService.avanzarAñoLectivo(id, request.templateId()));
    }

    // Fase 6.7: cobro puntual (derecho de examen, recursada, título, trámite) — no pasa por
    // InvoiceGenerationService ni por ninguna plantilla anual.
    @PostMapping("/program-enrollments/{id}/invoices/ad-hoc")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMINISTRATIVO')")
    public InvoiceResponse createAdHocInvoice(@PathVariable Long id, @Valid @RequestBody CreateAdHocInvoiceRequest request) {
        return InvoiceResponse.from(adHocInvoiceService.createForProgramEnrollment(id, request));
    }
}
