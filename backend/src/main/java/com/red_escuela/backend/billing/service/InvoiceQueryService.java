package com.red_escuela.backend.billing.service;

import com.red_escuela.backend.billing.dto.PriceBreakdownLineResponse;
import com.red_escuela.backend.billing.dto.PriceBreakdownPaymentResponse;
import com.red_escuela.backend.billing.dto.PriceBreakdownResponse;
import com.red_escuela.backend.billing.entity.PaymentAllocation;
import com.red_escuela.backend.billing.entity.PriceRevision;
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
import com.red_escuela.backend.enrollment.repository.CourseEnrollmentRepository;
import com.red_escuela.backend.enrollment.repository.ProgramEnrollmentRepository;
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
    private final CourseEnrollmentRepository courseEnrollmentRepository;
    private final ProgramEnrollmentRepository programEnrollmentRepository;
    private final PriceRevisionService priceRevisionService;
    private final Clock clock;

    public InvoiceQueryService(InvoiceRepository invoiceRepository, InvoiceViewLoader invoiceViewLoader, CourseEnrollmentRepository courseEnrollmentRepository,
                               ProgramEnrollmentRepository programEnrollmentRepository, PriceRevisionService priceRevisionService, Clock clock) {
        this.invoiceRepository = invoiceRepository;
        this.invoiceViewLoader = invoiceViewLoader;
        this.courseEnrollmentRepository = courseEnrollmentRepository;
        this.programEnrollmentRepository = programEnrollmentRepository;
        this.priceRevisionService = priceRevisionService;
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
        return courseEnrollmentRepository.findById(courseEnrollmentId)
                .orElseThrow(() -> new CourseEnrollmentNotFoundException(courseEnrollmentId));
    }

    public ProgramEnrollment programBillable(Long programEnrollmentId) {
        return programEnrollmentRepository.findById(programEnrollmentId)
                .orElseThrow(() -> new ProgramEnrollmentNotFoundException(programEnrollmentId));
    }

    @Transactional(readOnly = true)
    public List<InvoiceView> byOwnCourse(Long studentId, Long courseEnrollmentId) {
        CourseEnrollment enrollment = courseEnrollmentRepository.findById(courseEnrollmentId)
                .orElseThrow(() -> new CourseEnrollmentNotFoundException(courseEnrollmentId));
        if (!enrollment.getStudent().getId().equals(studentId)) {
            throw new NotOwnEnrollmentException(studentId, courseEnrollmentId);
        }
        return byCourse(courseEnrollmentId);
    }

    @Transactional(readOnly = true)
    public List<InvoiceView> byOwnProgram(Long studentId, Long programEnrollmentId) {
        ProgramEnrollment enrollment = programEnrollmentRepository.findById(programEnrollmentId)
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
        List<PriceBreakdownPaymentResponse> payments = invoiceViewLoader.loadInvoiceView(invoice).allocations().stream().map(this::toPayment).toList();
        return new PriceBreakdownResponse(
                invoice.getId(), line.getFeeType().name(), line.getPeriod().toString(), invoice.getAmounts().linesTotal().value(),
                resolvePriceRevisionDescription(invoice),
                lines, invoice.getAmounts().total().value(), payments, invoice.getAmounts().outstandingAmount().value(), statusNameOf(invoice));
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
        for (CourseEnrollment enrollment : courseEnrollmentRepository.findByStudentId(studentId)) {
            if (hasAnyUnpaidInstallment(invoiceRepository.findByCourseEnrollmentIdOrderByPeriod(enrollment.getId()))) {
                return true;
            }
        }
        for (ProgramEnrollment enrollment : programEnrollmentRepository.findByStudentId(studentId)) {
            if (hasAnyUnpaidInstallment(invoiceRepository.findByProgramEnrollmentIdOrderByPeriod(enrollment.getId()))) {
                return true;
            }
        }
        return false;
    }

    public boolean hasOverdueDebt(Long studentId) {
        LocalDate today = LocalDate.now(clock);
        for (CourseEnrollment enrollment : courseEnrollmentRepository.findByStudentId(studentId)) {
            if (invoiceRepository.findByCourseEnrollmentIdOrderByPeriod(enrollment.getId()).stream().anyMatch(i -> isOverdue(i, today))) {
                return true;
            }
        }
        for (ProgramEnrollment enrollment : programEnrollmentRepository.findByStudentId(studentId)) {
            if (invoiceRepository.findByProgramEnrollmentIdOrderByPeriod(enrollment.getId()).stream().anyMatch(i -> isOverdue(i, today))) {
                return true;
            }
        }
        return false;
    }

    private String resolvePriceRevisionDescription(Invoice invoice) {
        InvoiceLine line = invoice.getPrimaryLine();
        if (invoice.findCourseEnrollmentId().isPresent()) {
            CourseEnrollment enrollment = courseEnrollmentRepository.findById(invoice.findCourseEnrollmentId().get()).orElse(null);
            return enrollment == null ? null : priceRevisionService.findRevisionForCourse(enrollment.getCourse().getId(), line.getFeeType(), invoice.getDueDate())
                    .map(PriceRevision::getDescription).orElse(null);
        }
        if (invoice.findProgramEnrollmentId().isPresent()) {
            ProgramEnrollment enrollment = programEnrollmentRepository.findById(invoice.findProgramEnrollmentId().get()).orElse(null);
            return enrollment == null ? null : priceRevisionService.findRevisionForProgram(enrollment.getProgram().getId(), line.getFeeType(), invoice.getDueDate())
                    .map(PriceRevision::getDescription).orElse(null);
        }
        return null;
    }

    private static String statusNameOf(Invoice invoice) {
        return invoice.getDocumentStatus() == InvoiceDocumentStatus.DISCARDED ? "CANCELLED" : invoice.getPaymentStatus().name();
    }

    private PriceBreakdownLineResponse toLine(InvoiceDiscount discount) {
        Money amount = discount.getDiscountAmount().value();
        return new PriceBreakdownLineResponse(discount.getDiscountType().name(), Money.zero(amount.currency()).minus(amount), discount.getConcept(), "Sistema", null);
    }

    private PriceBreakdownPaymentResponse toPayment(PaymentAllocation allocation) {
        return new PriceBreakdownPaymentResponse(
                allocation.getPayment().getId(), allocation.getAmountApplied(),
                allocation.getPayment().getMethod().name(), allocation.getPayment().getPaidAt());
    }

    private boolean hasAnyUnpaidInstallment(List<Invoice> invoices) {
        return invoices.stream().anyMatch(i -> i.getPaymentStatus() == InvoicePaymentStatus.PENDING || i.getPaymentStatus() == InvoicePaymentStatus.OVERDUE);
    }

    private boolean isOverdue(Invoice invoice, LocalDate today) {
        return invoice.getPaymentStatus() == InvoicePaymentStatus.OVERDUE
                || (invoice.getPaymentStatus() == InvoicePaymentStatus.PENDING && invoice.getDueDate().isBefore(today));
    }
}
