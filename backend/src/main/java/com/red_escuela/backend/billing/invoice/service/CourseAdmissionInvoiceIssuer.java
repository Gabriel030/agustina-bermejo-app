package com.red_escuela.backend.billing.invoice.service;

import com.red_escuela.backend.billing.exception.ChargeRejectedException;
import com.red_escuela.backend.billing.exception.DuplicateChargeException;
import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.billing.invoice.domain.issuing.InvoiceIssuingResult;
import com.red_escuela.backend.billing.invoice.repository.InvoiceRepository;
import com.red_escuela.backend.enrollment.api.CourseAdmissionInvoicePort;
import com.red_escuela.backend.enrollment.api.CourseAdmissionInvoiceView;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;

@Service
public class CourseAdmissionInvoiceIssuer implements CourseAdmissionInvoicePort {

    private final InvoiceIssuingService invoiceIssuingService;
    private final InvoiceRepository invoiceRepository;
    private final Clock clock;

    public CourseAdmissionInvoiceIssuer(InvoiceIssuingService invoiceIssuingService, InvoiceRepository invoiceRepository, Clock clock) {
        this.invoiceIssuingService = invoiceIssuingService;
        this.invoiceRepository = invoiceRepository;
        this.clock = clock;
    }

    @Override
    @Transactional
    public CourseAdmissionInvoiceView issueCourseAdmissionInvoice(Long studentId, Long courseId) {
        Invoice invoice = switch (invoiceIssuingService.issueInvoiceInCallerTransaction(
                InvoiceIssuingRequestFactory.createCourseAdmissionRequest(studentId, courseId, LocalDate.now(clock)))) {
            case InvoiceIssuingResult.InvoiceIssued issued -> issued.invoice();
            case InvoiceIssuingResult.InvoiceIssuingRejected rejected -> throw new ChargeRejectedException(rejected.evaluationResult());
            case InvoiceIssuingResult.InvoiceAlreadyIssued duplicated -> throw new DuplicateChargeException("course admission");
        };
        return new CourseAdmissionInvoiceView(invoice.getId(), invoice.isPayable());
    }

    @Override
    @Transactional
    public void assignInvoiceToCourseEnrollment(Long invoiceId, Long courseEnrollmentId) {
        invoiceRepository.findById(invoiceId).orElseThrow().assignInvoiceToCourseEnrollment(courseEnrollmentId);
    }
}
