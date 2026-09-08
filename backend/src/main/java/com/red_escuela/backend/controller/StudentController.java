package com.red_escuela.backend.controller;

import com.red_escuela.backend.billing.dto.CreateCreditNoteRequest;
import com.red_escuela.backend.billing.dto.CreditNoteResponse;
import com.red_escuela.backend.billing.dto.EnrollmentBillingStatusResponse;
import com.red_escuela.backend.billing.dto.InvoiceResponse;
import com.red_escuela.backend.dto.StudentResponse;
import com.red_escuela.backend.dto.WithdrawStudentRequest;
import com.red_escuela.backend.entity.BillableEnrollment;
import com.red_escuela.backend.billing.entity.Invoice;
import com.red_escuela.backend.entity.StatusType;
import com.red_escuela.backend.entity.StudentOnboardingStatus;
import com.red_escuela.backend.exception.StudentNotFoundException;
import com.red_escuela.backend.repository.CourseEnrollmentRepository;
import com.red_escuela.backend.repository.ProgramEnrollmentRepository;
import com.red_escuela.backend.repository.StudentRepository;
import com.red_escuela.backend.billing.service.CreditNoteService;
import com.red_escuela.backend.billing.service.InvoiceQueryService;
import com.red_escuela.backend.service.StudentService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Stream;

@RestController
@RequestMapping("/students")
public class StudentController {

    private final StudentService studentService;
    private final InvoiceQueryService invoiceQueryService;
    private final CourseEnrollmentRepository courseEnrollmentRepository;
    private final ProgramEnrollmentRepository programEnrollmentRepository;
    private final CreditNoteService creditNoteService;
    private final StudentRepository studentRepository;

    public StudentController(StudentService studentService, InvoiceQueryService invoiceQueryService,
                              CourseEnrollmentRepository courseEnrollmentRepository, ProgramEnrollmentRepository programEnrollmentRepository,
                              CreditNoteService creditNoteService, StudentRepository studentRepository) {
        this.studentService = studentService;
        this.invoiceQueryService = invoiceQueryService;
        this.courseEnrollmentRepository = courseEnrollmentRepository;
        this.programEnrollmentRepository = programEnrollmentRepository;
        this.creditNoteService = creditNoteService;
        this.studentRepository = studentRepository;
    }

    // Matriz de permisos (sección 3): Director tiene R sobre el legajo de un alumno, a
    // diferencia de las operaciones de escritura de abajo (baja/reset), que son exclusivas
    // de Preceptor/Administrativo.
    @GetMapping
    @PreAuthorize("hasRole('PRECEPTOR') or hasRole('ADMINISTRATIVO') or hasRole('DIRECTOR')")
    public List<StudentResponse> listar(
            @RequestParam(required = false) StatusType status,
            @RequestParam(required = false) StudentOnboardingStatus onboardingStatus) {
        return studentService.listar(status, onboardingStatus).stream().map(StudentResponse::from).toList();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('PRECEPTOR') or hasRole('ADMINISTRATIVO') or hasRole('DIRECTOR')")
    public StudentResponse verLegajo(@PathVariable Long id) {
        return StudentResponse.from(studentService.verLegajo(id));
    }

    @PostMapping("/{id}/withdraw")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('PRECEPTOR') or hasRole('ADMINISTRATIVO')")
    public void withdraw(@PathVariable Long id, @Valid @RequestBody WithdrawStudentRequest request) {
        studentService.bajaAlumno(id, request);
    }

    @PostMapping("/{id}/reset-password")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @PreAuthorize("hasRole('PRECEPTOR') or hasRole('ADMINISTRATIVO')")
    public void resetearPassword(@PathVariable Long id) {
        studentService.resetearPassword(id);
    }

    // Versión mínima de esta fase (Fase 6.3): billingStanding + invoices por cada enrollment del
    // alumno. El detalle completo de "vista 360°" (deuda total, historial de pagos, etc.) es de
    // Fase 6.11 (Reporting).
    @GetMapping("/{id}/billing-overview")
    @PreAuthorize("hasAnyRole('ADMINISTRATIVO', 'DIRECTOR')")
    public List<EnrollmentBillingStatusResponse> billingOverview(@PathVariable Long id) {
        if (!studentRepository.existsById(id)) {
            throw new StudentNotFoundException(id);
        }
        return Stream.concat(
                courseEnrollmentRepository.findByStudentId(id).stream()
                        .map(enrollment -> toStatusResponse(enrollment, invoiceQueryService.porCurso(enrollment.getId()))),
                programEnrollmentRepository.findByStudentId(id).stream()
                        .map(enrollment -> toStatusResponse(enrollment, invoiceQueryService.porCarrera(enrollment.getId()))))
                .toList();
    }

    private EnrollmentBillingStatusResponse toStatusResponse(BillableEnrollment billable, List<Invoice> invoices) {
        return new EnrollmentBillingStatusResponse(billable.getBillingStanding().name(), billable.getBillingStandingUpdatedAt(),
                invoices.stream().map(InvoiceResponse::from).toList());
    }

    @GetMapping("/{id}/credit-notes")
    @PreAuthorize("hasAnyRole('ADMINISTRATIVO', 'DIRECTOR')")
    public List<CreditNoteResponse> creditNotes(@PathVariable Long id) {
        return creditNoteService.listForStudent(id).stream().map(CreditNoteResponse::from).toList();
    }

    // Solo ADMINISTRATIVE_CORRECTION/OTHER (CreditNoteService.createManual lo valida) — las otras
    // dos razones (EARLY_WITHDRAWAL_UPFRONT_UNUSED/RETROACTIVE_SCHOLARSHIP) las genera el sistema.
    @PostMapping("/{id}/credit-notes")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMINISTRATIVO', 'DIRECTOR')")
    public CreditNoteResponse createCreditNote(@PathVariable Long id, @Valid @RequestBody CreateCreditNoteRequest request) {
        return CreditNoteResponse.from(creditNoteService.createManual(id, request));
    }
}
