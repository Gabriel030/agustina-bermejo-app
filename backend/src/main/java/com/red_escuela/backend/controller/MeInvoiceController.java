package com.red_escuela.backend.controller;

import com.red_escuela.backend.dto.BillingStandingResponse;
import com.red_escuela.backend.dto.EnrollmentBillingStatusResponse;
import com.red_escuela.backend.dto.InvoiceResponse;
import com.red_escuela.backend.dto.PaymentReceiptResponse;
import com.red_escuela.backend.dto.PriceBreakdownResponse;
import com.red_escuela.backend.entity.BillableEnrollment;
import com.red_escuela.backend.entity.Invoice;
import com.red_escuela.backend.repository.CourseEnrollmentRepository;
import com.red_escuela.backend.repository.ProgramEnrollmentRepository;
import com.red_escuela.backend.service.CurrentStudentService;
import com.red_escuela.backend.service.InvoiceQueryService;
import com.red_escuela.backend.service.PaymentReceiptService;
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
    private final PaymentReceiptService paymentReceiptService;
    private final CourseEnrollmentRepository courseEnrollmentRepository;
    private final ProgramEnrollmentRepository programEnrollmentRepository;

    public MeInvoiceController(InvoiceQueryService invoiceQueryService, CurrentStudentService currentStudentService,
                                PaymentReceiptService paymentReceiptService, CourseEnrollmentRepository courseEnrollmentRepository,
                                ProgramEnrollmentRepository programEnrollmentRepository) {
        this.invoiceQueryService = invoiceQueryService;
        this.currentStudentService = currentStudentService;
        this.paymentReceiptService = paymentReceiptService;
        this.courseEnrollmentRepository = courseEnrollmentRepository;
        this.programEnrollmentRepository = programEnrollmentRepository;
    }

    @PostMapping("/invoices/{id}/payment-receipts")
    @ResponseStatus(HttpStatus.CREATED)
    public PaymentReceiptResponse uploadReceipt(@PathVariable Long id, @RequestPart MultipartFile comprobante) {
        Long studentId = currentStudentService.resolveCurrentStudent().getId();
        return PaymentReceiptResponse.from(paymentReceiptService.subir(id, studentId, comprobante));
    }

    @GetMapping("/payment-receipts")
    public List<PaymentReceiptResponse> myReceipts() {
        Long studentId = currentStudentService.resolveCurrentStudent().getId();
        return paymentReceiptService.findOwn(studentId).stream().map(PaymentReceiptResponse::from).toList();
    }

    @GetMapping("/invoices/{id}/price-breakdown")
    public PriceBreakdownResponse priceBreakdown(@PathVariable Long id) {
        Long studentId = currentStudentService.resolveCurrentStudent().getId();
        return invoiceQueryService.ownPriceBreakdown(studentId, id);
    }

    @GetMapping("/course-enrollments/{id}/invoices")
    public EnrollmentBillingStatusResponse misCuotasDeCurso(@PathVariable Long id) {
        Long studentId = currentStudentService.resolveCurrentStudent().getId();
        return toStatusResponse(invoiceQueryService.courseBillable(id), invoiceQueryService.porCursoPropio(studentId, id));
    }

    @GetMapping("/program-enrollments/{id}/invoices")
    public EnrollmentBillingStatusResponse misCuotasDeCarrera(@PathVariable Long id) {
        Long studentId = currentStudentService.resolveCurrentStudent().getId();
        return toStatusResponse(invoiceQueryService.programBillable(id), invoiceQueryService.porCarreraPropia(studentId, id));
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
