package com.red_escuela.backend.billing.service;

import com.red_escuela.backend.billing.invoice.service.InvoiceViewLoader;

import com.red_escuela.backend.billing.invoice.service.InvoiceView;

import com.red_escuela.backend.billing.invoice.dto.PriceBreakdownCreditNoteResponse;
import com.red_escuela.backend.billing.dto.PriceBreakdownLineResponse;
import com.red_escuela.backend.billing.dto.PriceBreakdownPaymentResponse;
import com.red_escuela.backend.billing.dto.PriceBreakdownResponse;
import com.red_escuela.backend.billing.account.domain.PaymentAllocationSource;
import com.red_escuela.backend.billing.account.service.AllocationSourceQueryService;
import com.red_escuela.backend.billing.note.repository.CreditNoteRepository;
import com.red_escuela.backend.billing.pricing.service.PriceReader;
import com.red_escuela.backend.billing.exception.InvoiceNotFoundException;
import com.red_escuela.backend.billing.exception.NotOwnEnrollmentException;
import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.billing.invoice.domain.InvoiceDiscount;
import com.red_escuela.backend.billing.invoice.domain.InvoiceDocumentStatus;
import com.red_escuela.backend.billing.invoice.domain.InvoicePaymentStatus;
import com.red_escuela.backend.billing.invoice.domain.InvoiceLine;
import com.red_escuela.backend.billing.invoice.repository.InvoiceRepository;
import com.red_escuela.backend.enrollment.entity.CourseEnrollment;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.enrollment.exception.CourseEnrollmentNotFoundException;
import com.red_escuela.backend.enrollment.exception.ProgramEnrollmentNotFoundException;
import com.red_escuela.backend.shared.domain.Money;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

@Service
public class InvoiceQueryService {

    private final InvoiceRepository invoiceRepository;
    private final InvoiceViewLoader invoiceViewLoader;
    private final PriceReader priceReader;
    private final AllocationSourceQueryService allocationSources;
    private final CreditNoteRepository creditNoteRepository;
    private final com.red_escuela.backend.billing.payment.repository.PaymentRepository paymentRepository;
    private final com.red_escuela.backend.billing.repository.PaymentRepository legacyPaymentRepository;
    private final Clock clock;

    public InvoiceQueryService(InvoiceRepository invoiceRepository, InvoiceViewLoader invoiceViewLoader,
                               PriceReader priceReader, AllocationSourceQueryService allocationSources, CreditNoteRepository creditNoteRepository,
                               com.red_escuela.backend.billing.payment.repository.PaymentRepository paymentRepository,
                               com.red_escuela.backend.billing.repository.PaymentRepository legacyPaymentRepository, Clock clock) {
        this.invoiceRepository = invoiceRepository;
        this.invoiceViewLoader = invoiceViewLoader;
        this.priceReader = priceReader;
        this.allocationSources = allocationSources;
        this.creditNoteRepository = creditNoteRepository;
        this.paymentRepository = paymentRepository;
        this.legacyPaymentRepository = legacyPaymentRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<InvoiceView> byCourse(Long courseEnrollmentId) {
        return invoiceViewLoader.loadInvoiceViews(invoiceRepository.findByCourseEnrollmentIdOrderByPeriod(courseEnrollmentId));
    }

    @Transactional(readOnly = true)
    public List<InvoiceView> byProgram(Long programEnrollmentId) {
        return invoiceViewLoader.loadInvoiceViews(invoiceRepository.findByProgramEnrollmentIdOrderByPeriod(programEnrollmentId));
    }

    public CourseEnrollment courseBillable(Long courseEnrollmentId) {
        return invoiceViewLoader.findCourseEnrollment(courseEnrollmentId)
                .orElseThrow(() -> new CourseEnrollmentNotFoundException(courseEnrollmentId));
    }

    public ProgramEnrollment programBillable(Long programEnrollmentId) {
        return invoiceViewLoader.findProgramEnrollment(programEnrollmentId)
                .orElseThrow(() -> new ProgramEnrollmentNotFoundException(programEnrollmentId));
    }

    @Transactional(readOnly = true)
    public List<InvoiceView> byOwnCourse(Long studentId, Long courseEnrollmentId) {
        CourseEnrollment enrollment = invoiceViewLoader.findCourseEnrollment(courseEnrollmentId)
                .orElseThrow(() -> new CourseEnrollmentNotFoundException(courseEnrollmentId));
        if (!enrollment.getStudent().getId().equals(studentId)) {
            throw new NotOwnEnrollmentException(studentId, courseEnrollmentId);
        }
        return byCourse(courseEnrollmentId);
    }

    @Transactional(readOnly = true)
    public List<InvoiceView> byOwnProgram(Long studentId, Long programEnrollmentId) {
        ProgramEnrollment enrollment = invoiceViewLoader.findProgramEnrollment(programEnrollmentId)
                .orElseThrow(() -> new ProgramEnrollmentNotFoundException(programEnrollmentId));
        if (!enrollment.getStudent().getId().equals(studentId)) {
            throw new NotOwnEnrollmentException(studentId, programEnrollmentId);
        }
        return byProgram(programEnrollmentId);
    }

    @Transactional(readOnly = true)
    public PriceBreakdownResponse priceBreakdown(Long invoiceId) {
        Invoice invoice = invoiceRepository.findInvoiceWithLinesById(invoiceId).orElseThrow(() -> new InvoiceNotFoundException(invoiceId));
        InvoiceLine line = invoice.getPrimaryLine();
        List<PriceBreakdownLineResponse> lines = invoice.getLines().stream()
                .filter(l -> l.getDiscount() != null)
                .map(l -> toLine(l.getDiscount()))
                .toList();
        List<PaymentAllocationSource> sources = allocationSources.findCurrentSourcesByInvoiceId(List.of(invoiceId)).getOrDefault(invoiceId, List.of());
        List<PriceBreakdownPaymentResponse> payments = sources.stream().filter(source -> source.getPaymentId() != null).map(this::toPayment).toList();
        List<PriceBreakdownCreditNoteResponse> creditNotes = sources.stream().filter(source -> source.getCreditNoteId() != null).map(this::toCreditNote).toList();
        return new PriceBreakdownResponse(
                invoice.getId(), line.getFeeType().name(), line.getPeriod().toString(), invoice.getAmounts().linesTotal().value(),
                resolvePriceVersionDescription(invoice),
                lines, invoice.getAmounts().total().value(), payments, invoice.getAmounts().outstandingAmount().value(), statusNameOf(invoice), creditNotes);
    }

    @Transactional(readOnly = true)
    public PriceBreakdownResponse ownPriceBreakdown(Long studentId, Long invoiceId) {
        Long ownerId = invoiceRepository.findOwnerStudentIdByInvoiceId(invoiceId).orElseThrow(() -> new InvoiceNotFoundException(invoiceId));
        if (!studentId.equals(ownerId)) {
            throw new InvoiceNotFoundException(invoiceId);
        }
        return priceBreakdown(invoiceId);
    }

    public boolean hasDebt(Long studentId) {
        for (CourseEnrollment enrollment : invoiceViewLoader.findCourseEnrollmentsOfStudent(studentId)) {
            if (hasAnyUnpaidInstallment(invoiceRepository.findByCourseEnrollmentIdOrderByPeriod(enrollment.getId()))) {
                return true;
            }
        }
        for (ProgramEnrollment enrollment : invoiceViewLoader.findProgramEnrollmentsOfStudent(studentId)) {
            if (hasAnyUnpaidInstallment(invoiceRepository.findByProgramEnrollmentIdOrderByPeriod(enrollment.getId()))) {
                return true;
            }
        }
        return false;
    }

    public boolean hasOverdueDebt(Long studentId) {
        LocalDate today = LocalDate.now(clock);
        for (CourseEnrollment enrollment : invoiceViewLoader.findCourseEnrollmentsOfStudent(studentId)) {
            if (invoiceRepository.findByCourseEnrollmentIdOrderByPeriod(enrollment.getId()).stream().anyMatch(i -> isOverdue(i, today))) {
                return true;
            }
        }
        for (ProgramEnrollment enrollment : invoiceViewLoader.findProgramEnrollmentsOfStudent(studentId)) {
            if (invoiceRepository.findByProgramEnrollmentIdOrderByPeriod(enrollment.getId()).stream().anyMatch(i -> isOverdue(i, today))) {
                return true;
            }
        }
        return false;
    }

    private String resolvePriceVersionDescription(Invoice invoice) {
        Long priceVersionId = invoice.getPrimaryLine().getPriceVersionId();
        return priceVersionId == null ? null : priceReader.findPriceDescriptionOfVersion(priceVersionId);
    }

    private static String statusNameOf(Invoice invoice) {
        return invoice.getDocumentStatus() == InvoiceDocumentStatus.DISCARDED ? "CANCELLED" : invoice.getPaymentStatus().name();
    }

    private PriceBreakdownLineResponse toLine(InvoiceDiscount discount) {
        Money amount = discount.getDiscountAmount().value();
        return new PriceBreakdownLineResponse(discount.getDiscountType().name(), Money.zero(amount.currency()).minus(amount), discount.getConcept(), "Sistema", null);
    }

    private PriceBreakdownCreditNoteResponse toCreditNote(PaymentAllocationSource source) {
        String number = creditNoteRepository.findCreditNoteNumberById(source.getCreditNoteId())
                .orElseThrow(() -> new IllegalStateException("Credit note " + source.getCreditNoteId() + " referenced by an allocation source does not exist"));
        return new PriceBreakdownCreditNoteResponse(source.getCreditNoteId(), number, source.getAmount().value(), "Nota de crédito " + number);
    }

    private PriceBreakdownPaymentResponse toPayment(PaymentAllocationSource source) {
        Money amount = source.getAmount().value();
        Long paymentId = source.getPaymentId();
        return paymentRepository.findById(paymentId)
                .map(payment -> new PriceBreakdownPaymentResponse(paymentId, amount, payment.getMethodType().name(),
                        payment.getPaidOn() == null ? payment.getRegisteredAt() : payment.getPaidOn().atStartOfDay()))
                .orElseGet(() -> legacyPaymentRepository.findById(paymentId)
                        .map(payment -> new PriceBreakdownPaymentResponse(paymentId, amount, payment.getMethod().name(), payment.getPaidAt()))
                        .orElseThrow(() -> new IllegalStateException("Payment " + paymentId + " referenced by an allocation source does not exist")));
    }

    private boolean hasAnyUnpaidInstallment(List<Invoice> invoices) {
        return invoices.stream().anyMatch(i -> i.getPaymentStatus() == InvoicePaymentStatus.PENDING || i.getPaymentStatus() == InvoicePaymentStatus.OVERDUE);
    }

    private boolean isOverdue(Invoice invoice, LocalDate today) {
        return invoice.getPaymentStatus() == InvoicePaymentStatus.OVERDUE
                || (invoice.getPaymentStatus() == InvoicePaymentStatus.PENDING && invoice.getDueDate().isBefore(today));
    }
}
