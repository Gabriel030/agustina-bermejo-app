package com.red_escuela.backend.billing.controller;
import com.red_escuela.backend.identity.service.CurrentStudentService;

import com.red_escuela.backend.billing.dto.EnrollmentPaymentStatusResponse;
import com.red_escuela.backend.billing.dto.MeEnrollmentBillingStatusResponse;
import com.red_escuela.backend.billing.usecase.invoice.GetOwnCourseEnrollmentBillingStatusUseCase;
import com.red_escuela.backend.billing.usecase.invoice.GetOwnProgramEnrollmentBillingStatusUseCase;
import com.red_escuela.backend.billing.dto.BankTransferReceiptResponse;
import com.red_escuela.backend.billing.invoice.service.BankTransferReceiptResponseAssembler;
import com.red_escuela.backend.billing.dto.PriceBreakdownResponse;
import com.red_escuela.backend.billing.api.EnrollmentPaymentStandingView;
import com.red_escuela.backend.enrollment.repository.CourseEnrollmentRepository;
import com.red_escuela.backend.enrollment.repository.ProgramEnrollmentRepository;
import com.red_escuela.backend.billing.service.InvoiceQueryService;
import com.red_escuela.backend.billing.dto.MpPreferenceResponse;
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
    private final BankTransferReceiptResponseAssembler receiptResponseAssembler;
    private final CurrentStudentService currentStudentService;
    private final UploadPaymentReceiptUseCase uploadPaymentReceiptUseCase;
    private final FindOwnPaymentReceiptsUseCase findOwnPaymentReceiptsUseCase;
    private final CourseEnrollmentRepository courseEnrollmentRepository;
    private final ProgramEnrollmentRepository programEnrollmentRepository;
    private final CreateMpPreferenceUseCase createMpPreferenceUseCase;
    private final GetOwnCourseEnrollmentBillingStatusUseCase getOwnCourseEnrollmentBillingStatusUseCase;
    private final GetOwnProgramEnrollmentBillingStatusUseCase getOwnProgramEnrollmentBillingStatusUseCase;

    public MeInvoiceController(InvoiceQueryService invoiceQueryService, CurrentStudentService currentStudentService,
                                UploadPaymentReceiptUseCase uploadPaymentReceiptUseCase, FindOwnPaymentReceiptsUseCase findOwnPaymentReceiptsUseCase,
                                CourseEnrollmentRepository courseEnrollmentRepository, ProgramEnrollmentRepository programEnrollmentRepository,
                                CreateMpPreferenceUseCase createMpPreferenceUseCase,
                                GetOwnCourseEnrollmentBillingStatusUseCase getOwnCourseEnrollmentBillingStatusUseCase,
                                GetOwnProgramEnrollmentBillingStatusUseCase getOwnProgramEnrollmentBillingStatusUseCase,
                                BankTransferReceiptResponseAssembler receiptResponseAssembler) {
        this.invoiceQueryService = invoiceQueryService;
        this.receiptResponseAssembler = receiptResponseAssembler;
        this.currentStudentService = currentStudentService;
        this.uploadPaymentReceiptUseCase = uploadPaymentReceiptUseCase;
        this.findOwnPaymentReceiptsUseCase = findOwnPaymentReceiptsUseCase;
        this.courseEnrollmentRepository = courseEnrollmentRepository;
        this.programEnrollmentRepository = programEnrollmentRepository;
        this.createMpPreferenceUseCase = createMpPreferenceUseCase;
        this.getOwnCourseEnrollmentBillingStatusUseCase = getOwnCourseEnrollmentBillingStatusUseCase;
        this.getOwnProgramEnrollmentBillingStatusUseCase = getOwnProgramEnrollmentBillingStatusUseCase;
    }

    @PostMapping("/invoices/{id}/mp-preference")
    @ResponseStatus(HttpStatus.CREATED)
    public MpPreferenceResponse createMpPreference(@PathVariable Long id) {
        Long studentId = currentStudentService.resolveCurrentStudent().getId();
        return createMpPreferenceUseCase.create(id, studentId);
    }

    @PostMapping("/invoices/{id}/bank-transfer-receipts")
    @ResponseStatus(HttpStatus.CREATED)
    public BankTransferReceiptResponse uploadReceipt(@PathVariable Long id, @RequestPart MultipartFile comprobante) {
        Long studentId = currentStudentService.resolveCurrentStudent().getId();
        return receiptResponseAssembler.assembleBankTransferReceiptResponse(uploadPaymentReceiptUseCase.upload(id, studentId, comprobante));
    }

    @GetMapping("/bank-transfer-receipts")
    public List<BankTransferReceiptResponse> myReceipts() {
        Long studentId = currentStudentService.resolveCurrentStudent().getId();
        return receiptResponseAssembler.assembleOwnReceiptResponses(findOwnPaymentReceiptsUseCase.findOwn(studentId));
    }

    @GetMapping("/invoices/{id}/price-breakdown")
    public PriceBreakdownResponse priceBreakdown(@PathVariable Long id) {
        Long studentId = currentStudentService.resolveCurrentStudent().getId();
        return invoiceQueryService.ownPriceBreakdown(studentId, id);
    }

    @GetMapping("/course-enrollments/{id}/invoices")
    public MeEnrollmentBillingStatusResponse myCourseInstallments(@PathVariable Long id) {
        Long studentId = currentStudentService.resolveCurrentStudent().getId();
        return getOwnCourseEnrollmentBillingStatusUseCase.get(studentId, id);
    }

    @GetMapping("/program-enrollments/{id}/invoices")
    public MeEnrollmentBillingStatusResponse myProgramInstallments(@PathVariable Long id) {
        Long studentId = currentStudentService.resolveCurrentStudent().getId();
        return getOwnProgramEnrollmentBillingStatusUseCase.get(studentId, id);
    }

    @GetMapping("/enrollment-payment-status")
    public List<EnrollmentPaymentStatusResponse> myPaymentStatus() {
        Long studentId = currentStudentService.resolveCurrentStudent().getId();
        return paymentStatusOf(studentId);
    }

    private List<EnrollmentPaymentStatusResponse> paymentStatusOf(Long studentId) {
        return Stream.concat(
                courseEnrollmentRepository.findByStudentId(studentId).stream()
                        .map(enrollment -> enrollmentPaymentStatusResponse("CourseEnrollment", enrollment.getId(), invoiceQueryService.courseStanding(enrollment.getId()))),
                programEnrollmentRepository.findByStudentId(studentId).stream()
                        .map(enrollment -> enrollmentPaymentStatusResponse("ProgramEnrollment", enrollment.getId(), invoiceQueryService.programStanding(enrollment.getId()))))
                .toList();
    }

    private EnrollmentPaymentStatusResponse enrollmentPaymentStatusResponse(String enrollmentType, Long enrollmentId, EnrollmentPaymentStandingView standing) {
        return new EnrollmentPaymentStatusResponse(enrollmentType, enrollmentId, EnrollmentPaymentStandingView.statusOrUpToDate(standing).name(), EnrollmentPaymentStandingView.statusChangedAtOrNull(standing));
    }

}
