package com.red_escuela.backend.billing.usecase.paymentreceipt;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.red_escuela.backend.billing.dto.PaymentAllocationRequest;
import com.red_escuela.backend.billing.dto.RegisterPaymentRequest;
import com.red_escuela.backend.billing.entity.Invoice;
import com.red_escuela.backend.billing.entity.PaymentMethod;
import com.red_escuela.backend.billing.entity.PaymentReceipt;
import com.red_escuela.backend.billing.entity.PaymentReceiptStatus;
import com.red_escuela.backend.billing.exception.InvalidPaymentReceiptStateException;
import com.red_escuela.backend.billing.repository.InvoiceRepository;
import com.red_escuela.backend.billing.repository.PaymentReceiptRepository;
import com.red_escuela.backend.billing.service.PaymentReceiptFinder;
import com.red_escuela.backend.billing.usecase.payment.RegisterPaymentUseCase;
import com.red_escuela.backend.enrollment.dto.CreateCourseEnrollmentRequest;
import com.red_escuela.backend.enrollment.entity.CourseEnrollment;
import com.red_escuela.backend.enrollment.entity.CourseEnrollmentApplication;
import com.red_escuela.backend.enrollment.exception.InvalidSectionOverridesException;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.enrollment.repository.CourseEnrollmentApplicationRepository;
import com.red_escuela.backend.enrollment.service.CourseEnrollmentService;
import com.red_escuela.backend.shared.UseCase;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@UseCase
public class ApprovePaymentReceiptService implements ApprovePaymentReceiptUseCase {

    private final PaymentReceiptRepository paymentReceiptRepository;
    private final InvoiceRepository invoiceRepository;
    private final PaymentReceiptFinder paymentReceiptFinder;
    private final CourseEnrollmentApplicationRepository courseEnrollmentApplicationRepository;
    private final CourseEnrollmentService courseEnrollmentService;
    private final RegisterPaymentUseCase registerPaymentUseCase;
    private final ObjectMapper objectMapper;

    public ApprovePaymentReceiptService(PaymentReceiptRepository paymentReceiptRepository, InvoiceRepository invoiceRepository,
                                         PaymentReceiptFinder paymentReceiptFinder, CourseEnrollmentApplicationRepository courseEnrollmentApplicationRepository,
                                         CourseEnrollmentService courseEnrollmentService, RegisterPaymentUseCase registerPaymentUseCase,
                                         ObjectMapper objectMapper) {
        this.paymentReceiptRepository = paymentReceiptRepository;
        this.invoiceRepository = invoiceRepository;
        this.paymentReceiptFinder = paymentReceiptFinder;
        this.courseEnrollmentApplicationRepository = courseEnrollmentApplicationRepository;
        this.courseEnrollmentService = courseEnrollmentService;
        this.registerPaymentUseCase = registerPaymentUseCase;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public PaymentReceipt approve(Long id, Person decidedBy) {
        PaymentReceipt receipt = paymentReceiptFinder.findOrThrow(id);
        if (receipt.getStatus() != PaymentReceiptStatus.PENDING_REVIEW) {
            throw new InvalidPaymentReceiptStateException(id, receipt.getStatus().name());
        }
        receipt.setStatus(PaymentReceiptStatus.APPROVED);
        receipt.setReviewedBy(decidedBy);
        receipt.setReviewedAt(LocalDateTime.now());

        // Mismo camino que un pago cargado a mano por Administrativo — el comprobante no
        // distingue efectivo/transferencia, siempre es "subí un archivo", así que method =
        // TRANSFER. RegisterPaymentService ya deja la Invoice en PAID/PARTIALLY_PAID según
        // corresponda; no se toca acá.
        Invoice invoice = receipt.getInvoice();
        registerPaymentUseCase.register(new RegisterPaymentRequest(
                List.of(new PaymentAllocationRequest(invoice.getId(), invoice.getRemainingBalance())),
                PaymentMethod.TRANSFER, false, null, null, null, null), decidedBy);

        // A course has no separate "application" decision — approving the receipt of its first
        // installment is the only trigger for the real enrollment (business decision, same as
        // before: neither a program nor a course grants access until the payment is approved).
        courseEnrollmentApplicationRepository.findByPaymentReceiptId(id)
                .filter(application -> application.getCourseEnrollment() == null)
                .ifPresent(this::matricularCurso);

        return paymentReceiptRepository.save(receipt);
    }

    private void matricularCurso(CourseEnrollmentApplication application) {
        Map<Long, Long> overrides = deserializarOverrides(application.getSectionOverrides());
        CourseEnrollment enrollment = courseEnrollmentService.enrollSelfService(
                application.getStudent().getId(), new CreateCourseEnrollmentRequest(application.getCourse().getId(), overrides));
        application.setCourseEnrollment(enrollment);
        courseEnrollmentApplicationRepository.save(application);

        // Only now does the CourseEnrollment exist — the first-installment invoice (created
        // ad-hoc when the application was submitted, with no enrollment link possible yet) gets
        // linked retroactively, so the recurring billing engine doesn't generate a duplicate for
        // the month already paid this way.
        Invoice invoice = application.getPaymentReceipt().getInvoice();
        invoice.setCourseEnrollment(enrollment);
        invoiceRepository.save(invoice);
    }

    private Map<Long, Long> deserializarOverrides(String sectionOverridesJson) {
        if (sectionOverridesJson == null || sectionOverridesJson.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(sectionOverridesJson, new TypeReference<Map<Long, Long>>() {
            });
        } catch (JsonProcessingException e) {
            throw new InvalidSectionOverridesException(sectionOverridesJson, e);
        }
    }
}
