package com.red_escuela.backend.controller;

import com.red_escuela.backend.billing.dto.CreateAdHocInvoiceRequest;
import com.red_escuela.backend.billing.dto.InvoiceResponse;
import com.red_escuela.backend.billing.usecase.invoice.CreateAdHocInvoiceForCourseEnrollmentUseCase;
import com.red_escuela.backend.dto.CourseEnrollmentResponse;
import com.red_escuela.backend.dto.CourseProgressResponse;
import com.red_escuela.backend.dto.CreateCourseEnrollmentRequest;
import com.red_escuela.backend.service.CourseEnrollmentService;
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
public class CourseEnrollmentController {

    private final CourseEnrollmentService courseEnrollmentService;
    private final CreateAdHocInvoiceForCourseEnrollmentUseCase createAdHocInvoiceUseCase;

    public CourseEnrollmentController(CourseEnrollmentService courseEnrollmentService, CreateAdHocInvoiceForCourseEnrollmentUseCase createAdHocInvoiceUseCase) {
        this.courseEnrollmentService = courseEnrollmentService;
        this.createAdHocInvoiceUseCase = createAdHocInvoiceUseCase;
    }

    @PostMapping("/students/{id}/course-enrollments")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('PRECEPTOR') or hasRole('ADMINISTRATIVO')")
    public CourseEnrollmentResponse matricular(@PathVariable Long id, @Valid @RequestBody CreateCourseEnrollmentRequest request) {
        return CourseEnrollmentResponse.from(courseEnrollmentService.matricular(id, request));
    }

    @PostMapping("/course-enrollments/{id}/withdraw")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('PRECEPTOR') or hasRole('ADMINISTRATIVO')")
    public void darDeBaja(@PathVariable Long id) {
        courseEnrollmentService.darDeBaja(id);
    }

    @GetMapping("/students/{id}/course-enrollments")
    @PreAuthorize("hasRole('PRECEPTOR') or hasRole('ADMINISTRATIVO') or hasRole('DIRECTOR')")
    public List<CourseEnrollmentResponse> listarPorAlumno(@PathVariable Long id) {
        return courseEnrollmentService.listarPorAlumno(id).stream().map(CourseEnrollmentResponse::from).toList();
    }

    @GetMapping("/course-enrollments/{id}")
    @PreAuthorize("hasRole('PRECEPTOR') or hasRole('ADMINISTRATIVO') or hasRole('DIRECTOR')")
    public CourseEnrollmentResponse verDetalle(@PathVariable Long id) {
        return CourseEnrollmentResponse.from(courseEnrollmentService.verDetalle(id));
    }

    @GetMapping("/course-enrollments/{id}/progress")
    @PreAuthorize("hasRole('PRECEPTOR') or hasRole('ADMINISTRATIVO') or hasRole('DIRECTOR')")
    public CourseProgressResponse verProgreso(@PathVariable Long id) {
        return courseEnrollmentService.calcularProgreso(id);
    }

    // Fase 6.7: cobro puntual (derecho de examen, recursada, título, trámite) — no pasa por
    // InvoiceGenerationService ni por ninguna plantilla anual.
    @PostMapping("/course-enrollments/{id}/invoices/ad-hoc")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMINISTRATIVO')")
    public InvoiceResponse createAdHocInvoice(@PathVariable Long id, @Valid @RequestBody CreateAdHocInvoiceRequest request) {
        return InvoiceResponse.from(createAdHocInvoiceUseCase.create(id, request));
    }
}
