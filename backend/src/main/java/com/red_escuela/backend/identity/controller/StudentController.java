package com.red_escuela.backend.identity.controller;

import com.red_escuela.backend.billing.dto.CreateCreditNoteRequest;
import com.red_escuela.backend.billing.dto.CreditNoteResponse;
import com.red_escuela.backend.billing.dto.EnrollmentBillingStatusResponse;
import com.red_escuela.backend.billing.dto.InvoiceResponse;
import com.red_escuela.backend.enrollment.dto.StudentDocumentResponse;
import com.red_escuela.backend.identity.dto.StudentRecordResponse;
import com.red_escuela.backend.identity.dto.StudentResponse;
import com.red_escuela.backend.identity.dto.WithdrawStudentRequest;
import com.red_escuela.backend.enrollment.repository.StudentDocumentRepository;
import com.red_escuela.backend.billing.entity.Invoice;
import com.red_escuela.backend.enrollment.entity.CourseEnrollment;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.entity.StatusType;
import com.red_escuela.backend.identity.entity.StudentOnboardingStatus;
import com.red_escuela.backend.identity.exception.StudentNotFoundException;
import com.red_escuela.backend.identity.usecase.student.GetStudentRecordUseCase;
import com.red_escuela.backend.identity.usecase.student.ListStudentsCommand;
import com.red_escuela.backend.identity.usecase.student.ListStudentsUseCase;
import com.red_escuela.backend.identity.usecase.student.ResetStudentPasswordUseCase;
import com.red_escuela.backend.identity.usecase.student.WithdrawStudentCommand;
import com.red_escuela.backend.identity.usecase.student.WithdrawStudentUseCase;
import com.red_escuela.backend.enrollment.repository.CourseEnrollmentRepository;
import com.red_escuela.backend.enrollment.repository.ProgramEnrollmentRepository;
import com.red_escuela.backend.identity.repository.StudentRepository;
import com.red_escuela.backend.billing.service.InvoiceQueryService;
import com.red_escuela.backend.billing.usecase.creditnote.CreateManualCreditNoteUseCase;
import com.red_escuela.backend.billing.usecase.creditnote.ListCreditNotesForStudentUseCase;
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

    private final ListStudentsUseCase listStudentsUseCase;
    private final GetStudentRecordUseCase getStudentRecordUseCase;
    private final WithdrawStudentUseCase withdrawStudentUseCase;
    private final ResetStudentPasswordUseCase resetStudentPasswordUseCase;
    private final InvoiceQueryService invoiceQueryService;
    private final CourseEnrollmentRepository courseEnrollmentRepository;
    private final ProgramEnrollmentRepository programEnrollmentRepository;
    private final ListCreditNotesForStudentUseCase listCreditNotesForStudentUseCase;
    private final CreateManualCreditNoteUseCase createManualCreditNoteUseCase;
    private final StudentRepository studentRepository;
    private final StudentDocumentRepository studentDocumentRepository;

    public StudentController(ListStudentsUseCase listStudentsUseCase, GetStudentRecordUseCase getStudentRecordUseCase,
                              WithdrawStudentUseCase withdrawStudentUseCase, ResetStudentPasswordUseCase resetStudentPasswordUseCase,
                              InvoiceQueryService invoiceQueryService, CourseEnrollmentRepository courseEnrollmentRepository,
                              ProgramEnrollmentRepository programEnrollmentRepository, ListCreditNotesForStudentUseCase listCreditNotesForStudentUseCase,
                              CreateManualCreditNoteUseCase createManualCreditNoteUseCase, StudentRepository studentRepository,
                              StudentDocumentRepository studentDocumentRepository) {
        this.listStudentsUseCase = listStudentsUseCase;
        this.getStudentRecordUseCase = getStudentRecordUseCase;
        this.withdrawStudentUseCase = withdrawStudentUseCase;
        this.resetStudentPasswordUseCase = resetStudentPasswordUseCase;
        this.invoiceQueryService = invoiceQueryService;
        this.courseEnrollmentRepository = courseEnrollmentRepository;
        this.programEnrollmentRepository = programEnrollmentRepository;
        this.listCreditNotesForStudentUseCase = listCreditNotesForStudentUseCase;
        this.createManualCreditNoteUseCase = createManualCreditNoteUseCase;
        this.studentRepository = studentRepository;
        this.studentDocumentRepository = studentDocumentRepository;
    }

    @GetMapping
    @PreAuthorize("hasRole('PRECEPTOR') or hasRole('ADMINISTRATIVO') or hasRole('DIRECTOR')")
    public List<StudentResponse> list(
            @RequestParam(required = false) StatusType status,
            @RequestParam(required = false) StudentOnboardingStatus onboardingStatus,
            @RequestParam(required = false) String search) {
        return listStudentsUseCase.list(new ListStudentsCommand(status, onboardingStatus, search))
                .stream().map(StudentResponse::from).toList();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('PRECEPTOR') or hasRole('ADMINISTRATIVO') or hasRole('DIRECTOR')")
    public StudentRecordResponse getRecord(@PathVariable Long id) {
        return StudentRecordResponse.from(getStudentRecordUseCase.getRecord(id));
    }

    @GetMapping("/{id}/documents")
    @PreAuthorize("hasRole('PRECEPTOR') or hasRole('ADMINISTRATIVO') or hasRole('DIRECTOR')")
    public List<StudentDocumentResponse> getDocuments(@PathVariable Long id) {
        if (!studentRepository.existsById(id)) throw new StudentNotFoundException(id);
        return studentDocumentRepository.findByStudentId(id).stream()
                .map(StudentDocumentResponse::from).toList();
    }

    @PostMapping("/{id}/withdraw")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('PRECEPTOR') or hasRole('ADMINISTRATIVO')")
    public void withdraw(@PathVariable Long id, @Valid @RequestBody WithdrawStudentRequest request) {
        withdrawStudentUseCase.withdraw(new WithdrawStudentCommand(id, request));
    }

    @PostMapping("/{id}/reset-password")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @PreAuthorize("hasRole('PRECEPTOR') or hasRole('ADMINISTRATIVO')")
    public void resetPassword(@PathVariable Long id) {
        resetStudentPasswordUseCase.resetPassword(id);
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

    private EnrollmentBillingStatusResponse toStatusResponse(CourseEnrollment enrollment, List<Invoice> invoices) {
        return new EnrollmentBillingStatusResponse(
                enrollment.getBillingStanding().name(), enrollment.getBillingStandingUpdatedAt(),
                invoices.stream().map(InvoiceResponse::from).toList(),
                "COURSE", enrollment.getId(), enrollment.getCourse().getName(), enrollment.getCourse().getId());
    }

    private EnrollmentBillingStatusResponse toStatusResponse(ProgramEnrollment enrollment, List<Invoice> invoices) {
        return new EnrollmentBillingStatusResponse(
                enrollment.getBillingStanding().name(), enrollment.getBillingStandingUpdatedAt(),
                invoices.stream().map(InvoiceResponse::from).toList(),
                "PROGRAM", enrollment.getId(), enrollment.getProgram().getName(), enrollment.getProgram().getId());
    }

    @GetMapping("/{id}/credit-notes")
    @PreAuthorize("hasAnyRole('ADMINISTRATIVO', 'DIRECTOR')")
    public List<CreditNoteResponse> creditNotes(@PathVariable Long id) {
        return listCreditNotesForStudentUseCase.list(id).stream().map(CreditNoteResponse::from).toList();
    }

    // Solo ADMINISTRATIVE_CORRECTION/OTHER (CreateManualCreditNoteService lo valida) — las otras
    // dos razones (EARLY_WITHDRAWAL_UPFRONT_UNUSED/RETROACTIVE_SCHOLARSHIP) las genera el sistema.
    @PostMapping("/{id}/credit-notes")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMINISTRATIVO', 'DIRECTOR')")
    public CreditNoteResponse createCreditNote(@PathVariable Long id, @Valid @RequestBody CreateCreditNoteRequest request) {
        return CreditNoteResponse.from(createManualCreditNoteUseCase.create(id, request));
    }
}
