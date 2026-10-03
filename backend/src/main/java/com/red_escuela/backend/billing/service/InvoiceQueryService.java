package com.red_escuela.backend.billing.service;

import com.red_escuela.backend.billing.dto.PriceBreakdownLineResponse;
import com.red_escuela.backend.billing.dto.PriceBreakdownPaymentResponse;
import com.red_escuela.backend.billing.dto.PriceBreakdownResponse;
import com.red_escuela.backend.enrollment.entity.BillableEnrollment;
import com.red_escuela.backend.enrollment.entity.CourseEnrollment;
import com.red_escuela.backend.billing.entity.Invoice;
import com.red_escuela.backend.billing.entity.InvoiceAdjustment;
import com.red_escuela.backend.billing.entity.InvoiceAdjustmentType;
import com.red_escuela.backend.billing.entity.InvoiceStatus;
import com.red_escuela.backend.billing.entity.PaymentAllocation;
import com.red_escuela.backend.billing.entity.PaymentPlanSelection;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.billing.entity.PriceRevision;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.enrollment.exception.CourseEnrollmentNotFoundException;
import com.red_escuela.backend.billing.exception.InvoiceNotFoundException;
import com.red_escuela.backend.exception.NotOwnEnrollmentException;
import com.red_escuela.backend.enrollment.exception.ProgramEnrollmentNotFoundException;
import com.red_escuela.backend.enrollment.repository.CourseEnrollmentRepository;
import com.red_escuela.backend.billing.repository.InvoiceRepository;
import com.red_escuela.backend.enrollment.repository.ProgramEnrollmentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
public class InvoiceQueryService {
    private final InvoiceRepository invoiceRepository;
    private final CourseEnrollmentRepository courseEnrollmentRepository;
    private final ProgramEnrollmentRepository programEnrollmentRepository;
    private final PriceRevisionService priceRevisionService;

    public InvoiceQueryService(InvoiceRepository invoiceRepository, CourseEnrollmentRepository courseEnrollmentRepository,
                                ProgramEnrollmentRepository programEnrollmentRepository, PriceRevisionService priceRevisionService) {
        this.invoiceRepository = invoiceRepository;
        this.courseEnrollmentRepository = courseEnrollmentRepository;
        this.programEnrollmentRepository = programEnrollmentRepository;
        this.priceRevisionService = priceRevisionService;
    }

    public List<Invoice> byCourse(Long courseEnrollmentId) {
        return invoiceRepository.findByCourseEnrollmentIdOrderByPeriod(courseEnrollmentId);
    }

    public List<Invoice> byProgram(Long programEnrollmentId) {
        return invoiceRepository.findByProgramEnrollmentIdOrderByPeriod(programEnrollmentId);
    }

    public BillableEnrollment courseBillable(Long courseEnrollmentId) {
        return courseEnrollmentRepository.findById(courseEnrollmentId)
                .orElseThrow(() -> new CourseEnrollmentNotFoundException(courseEnrollmentId));
    }

    public BillableEnrollment programBillable(Long programEnrollmentId) {
        return programEnrollmentRepository.findById(programEnrollmentId)
                .orElseThrow(() -> new ProgramEnrollmentNotFoundException(programEnrollmentId));
    }

    public List<Invoice> byOwnCourse(Long studentId, Long courseEnrollmentId) {
        CourseEnrollment enrollment = courseEnrollmentRepository.findById(courseEnrollmentId)
                .orElseThrow(() -> new CourseEnrollmentNotFoundException(courseEnrollmentId));
        if (!enrollment.getStudent().getId().equals(studentId)) {
            throw new NotOwnEnrollmentException(studentId, courseEnrollmentId);
        }
        return invoiceRepository.findByCourseEnrollmentIdOrderByPeriod(courseEnrollmentId);
    }

    @Transactional(readOnly = true)
    public PriceBreakdownResponse priceBreakdown(Long invoiceId) {
        Invoice invoice = invoiceRepository.findById(invoiceId).orElseThrow(() -> new InvoiceNotFoundException(invoiceId));
        List<PriceBreakdownLineResponse> lines = invoice.getAdjustments().stream()
                .sorted((a, b) -> Integer.compare(pipelineOrder(a.getType()), pipelineOrder(b.getType())))
                .map(this::toLine)
                .toList();
        List<PriceBreakdownPaymentResponse> payments = invoice.getAllocations().stream()
                .map(this::toPayment)
                .toList();
        return new PriceBreakdownResponse(
                invoice.getId(), invoice.getFeeType().name(), invoice.getPeriod().toString(), invoice.getBaseAmount(),
                resolvePriceRevisionDescription(invoice),
                lines, invoice.getAmountDue(), payments, invoice.getRemainingBalance(), invoice.getStatus().name());
    }

    private String resolvePriceRevisionDescription(Invoice invoice) {
        if (invoice.getCourseEnrollment() != null) {
            return priceRevisionService.findRevisionForCourse(
                            invoice.getCourseEnrollment().getCourse().getId(), invoice.getFeeType(), invoice.getDueDate())
                    .map(PriceRevision::getDescription).orElse(null);
        }
        if (invoice.getProgramEnrollment() != null) {
            return priceRevisionService.findRevisionForProgram(
                            invoice.getProgramEnrollment().getProgram().getId(), invoice.getFeeType(), invoice.getDueDate())
                    .map(PriceRevision::getDescription).orElse(null);
        }
        return null;
    }

    public PriceBreakdownResponse ownPriceBreakdown(Long studentId, Long invoiceId) {
        Invoice invoice = invoiceRepository.findById(invoiceId).orElseThrow(() -> new InvoiceNotFoundException(invoiceId));
        Long ownerId = invoice.getCourseEnrollment() != null ? invoice.getCourseEnrollment().getStudent().getId()
                : invoice.getProgramEnrollment() != null ? invoice.getProgramEnrollment().getStudent().getId()
                : null;
        if (!studentId.equals(ownerId)) {
            throw new InvoiceNotFoundException(invoiceId);
        }
        return priceBreakdown(invoiceId);
    }

    private int pipelineOrder(InvoiceAdjustmentType type) {
        return switch (type) {
            case SCHOLARSHIP -> 0;
            case PROMOTION -> 1;
            case DISCOUNT, WAIVE_DEBT -> 2;
            case SURCHARGE -> 3;
        };
    }

    private PriceBreakdownLineResponse toLine(InvoiceAdjustment adjustment) {
        Person appliedBy = adjustment.getAppliedBy();
        String source = appliedBy != null ? appliedBy.getName() + " " + appliedBy.getLastname() : "Sistema (mora automática)";
        String humanLabel = adjustment.getReason();
        if (adjustment.getType() == InvoiceAdjustmentType.PROMOTION) {
            humanLabel = humanLabel + " — " + describeEnablingSelection(adjustment.getInvoice().getPaymentPlanSelection());
        }
        return new PriceBreakdownLineResponse(
                adjustment.getType().name(), adjustment.signedAmount(), humanLabel, source, adjustment.getAppliedAt());
    }

    private String describeEnablingSelection(PaymentPlanSelection selection) {
        return "plan \"" + selection.getPaymentPlanOption().getDescription() + "\" elegido " + selection.getEffectiveFrom();
    }

    private PriceBreakdownPaymentResponse toPayment(PaymentAllocation allocation) {
        return new PriceBreakdownPaymentResponse(
                allocation.getPayment().getId(), allocation.getAmountApplied(),
                allocation.getPayment().getMethod().name(), allocation.getPayment().getPaidAt());
    }

    public List<Invoice> byOwnProgram(Long studentId, Long programEnrollmentId) {
        ProgramEnrollment enrollment = programEnrollmentRepository.findById(programEnrollmentId)
                .orElseThrow(() -> new ProgramEnrollmentNotFoundException(programEnrollmentId));
        if (!enrollment.getStudent().getId().equals(studentId)) {
            throw new NotOwnEnrollmentException(studentId, programEnrollmentId);
        }
        return invoiceRepository.findByProgramEnrollmentIdOrderByPeriod(programEnrollmentId);
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
        LocalDate today = LocalDate.now();
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

    private boolean hasAnyUnpaidInstallment(List<Invoice> invoices) {
        return invoices.stream().anyMatch(i -> i.getStatus() == InvoiceStatus.PENDING || i.getStatus() == InvoiceStatus.OVERDUE);
    }

    private boolean isOverdue(Invoice invoice, LocalDate today) {
        return invoice.getStatus() == InvoiceStatus.OVERDUE
                || (invoice.getStatus() == InvoiceStatus.PENDING && invoice.getDueDate().isBefore(today));
    }
}
