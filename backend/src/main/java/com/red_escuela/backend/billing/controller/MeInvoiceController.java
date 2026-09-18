package com.red_escuela.backend.billing.controller;
import com.red_escuela.backend.identity.service.CurrentStudentService;

import com.red_escuela.backend.billing.dto.BillingStandingResponse;
import com.red_escuela.backend.billing.dto.CreditNoteResponse;
import com.red_escuela.backend.billing.dto.EnrollmentBillingStatusResponse;
import com.red_escuela.backend.billing.dto.InvoiceResponse;
import com.red_escuela.backend.billing.dto.PaymentReceiptResponse;
import com.red_escuela.backend.billing.dto.PriceBreakdownResponse;
import com.red_escuela.backend.enrollment.entity.BillableEnrollment;
import com.red_escuela.backend.billing.entity.Invoice;
import com.red_escuela.backend.enrollment.repository.CourseEnrollmentRepository;
import com.red_escuela.backend.enrollment.repository.ProgramEnrollmentRepository;
import com.red_escuela.backend.billing.service.InvoiceQueryService;
import com.red_escuela.backend.billing.dto.MpPreferenceResponse;
import com.red_escuela.backend.billing.usecase.creditnote.ListCreditNotesForStudentUseCase;
import com.red_escuela.backend.billing.usecase.paymentgateway.CreateMpPreferenceUseCase;
import com.red_escuela.backend.billing.usecase.paymentreceipt.FindOwnPaymentReceiptsUseCase;
import com.red_escuela.backend.billing.usecase.paymentreceipt.UploadPaymentReceiptUseCase;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.stream.Stream;

@RestController
@RequestMapping("/me")
public class MeInvoiceController {

    private final InvoiceQueryService invoiceQueryService;
    private final CurrentStudentService currentStudentService;
    private final UploadPaymentReceiptUseCase uploadPaymentReceiptUseCase;
    private final FindOwnPaymentReceiptsUseCase findOwnPaymentReceiptsUseCase;
    private final CourseEnrollmentRepository courseEnrollmentRepository;
    private final ProgramEnrollmentRepository programEnrollmentRepository;
    private final ListCreditNotesForStudentUseCase listCreditNotesForStudentUseCase;
    private final CreateMpPreferenceUseCase createMpPreferenceUseCase;

    public MeInvoiceController(InvoiceQueryService invoiceQueryService, CurrentStudentService currentStudentService,
                                UploadPaymentReceiptUseCase uploadPaymentReceiptUseCase, FindOwnPaymentReceiptsUseCase findOwnPaymentReceiptsUseCase,
                                CourseEnrollmentRepository courseEnrollmentRepository, ProgramEnrollmentRepository programEnrollmentRepository,
                                ListCreditNotesForStudentUseCase listCreditNotesForStudentUseCase,
                                CreateMpPreferenceUseCase createMpPreferenceUseCase) {
        this.invoiceQueryService = invoiceQueryService;
        this.currentStudentService = currentStudentService;
        this.uploadPaymentReceiptUseCase = uploadPaymentReceiptUseCase;
        this.findOwnPaymentReceiptsUseCase = findOwnPaymentReceiptsUseCase;
        this.courseEnrollmentRepository = courseEnrollmentRepository;
        this.programEnrollmentRepository = programEnrollmentRepository;
        this.listCreditNotesForStudentUseCase = listCreditNotesForStudentUseCase;
        this.createMpPreferenceUseCase = createMpPreferenceUseCase;
    }

    @GetMapping("/credit-notes")
    public List<CreditNoteResponse> myCreditNotes() {
        Long studentId = currentStudentService.resolveCurrentStudent().getId();
        return listCreditNotesForStudentUseCase.list(studentId).stream().map(CreditNoteResponse::from).toList();
    }

    @PostMapping("/invoices/{id}/mp-preference")
    @ResponseStatus(HttpStatus.CREATED)
    public MpPreferenceResponse createMpPreference(@PathVariable Long id) {
        Long studentId = currentStudentService.resolveCurrentStudent().getId();
        return createMpPreferenceUseCase.create(id, studentId);
    }

    @PostMapping("/invoices/{id}/payment-receipts")
    @ResponseStatus(HttpStatus.CREATED)
    public PaymentReceiptResponse uploadReceipt(@PathVariable Long id, @RequestPart MultipartFile comprobante) {
        Long studentId = currentStudentService.resolveCurrentStudent().getId();
        return PaymentReceiptResponse.from(uploadPaymentReceiptUseCase.upload(id, studentId, comprobante));
    }

    @GetMapping("/payment-receipts")
    public List<PaymentReceiptResponse> myReceipts() {
        Long studentId = currentStudentService.resolveCurrentStudent().getId();
        return findOwnPaymentReceiptsUseCase.findOwn(studentId).stream().map(PaymentReceiptResponse::from).toList();
    }

    @GetMapping("/invoices/{id}/price-breakdown")
    public PriceBreakdownResponse priceBreakdown(@PathVariable Long id) {
        Long studentId = currentStudentService.resolveCurrentStudent().getId();
        return invoiceQueryService.ownPriceBreakdown(studentId, id);
    }

    @GetMapping("/course-enrollments/{id}/invoices")
    public EnrollmentBillingStatusResponse myCourseInstallments(@PathVariable Long id) {
        Long studentId = currentStudentService.resolveCurrentStudent().getId();
        return toStatusResponse(invoiceQueryService.courseBillable(id), invoiceQueryService.byOwnCourse(studentId, id));
    }

    @GetMapping("/program-enrollments/{id}/invoices")
    public EnrollmentBillingStatusResponse myProgramInstallments(@PathVariable Long id) {
        Long studentId = currentStudentService.resolveCurrentStudent().getId();
        return toStatusResponse(invoiceQueryService.programBillable(id), invoiceQueryService.byOwnProgram(studentId, id));
    }

    @GetMapping("/billing-standing")
    public List<BillingStandingResponse> myBillingStanding() {
        Long studentId = currentStudentService.resolveCurrentStudent().getId();
        return billingStandingOf(studentId);
    }

    private List<BillingStandingResponse> billingStandingOf(Long studentId) {
        return Stream.concat(
                courseEnrollmentRepository.findByStudentId(studentId).stream()
                        .map(enrollment -> billingStandingResponse("CourseEnrollment", enrollment.getId(), enrollment)),
                programEnrollmentRepository.findByStudentId(studentId).stream()
                        .map(enrollment -> billingStandingResponse("ProgramEnrollment", enrollment.getId(), enrollment)))
                .toList();
    }

    private BillingStandingResponse billingStandingResponse(String enrollmentType, Long enrollmentId, BillableEnrollment billable) {
        return new BillingStandingResponse(enrollmentType, enrollmentId, billable.getBillingStanding().name(), billable.getBillingStandingUpdatedAt());
    }

    private EnrollmentBillingStatusResponse toStatusResponse(BillableEnrollment billable, List<Invoice> invoices) {
        return new EnrollmentBillingStatusResponse(billable.getBillingStanding().name(), billable.getBillingStandingUpdatedAt(),
                invoices.stream().map(InvoiceResponse::from).toList());
    }
}
