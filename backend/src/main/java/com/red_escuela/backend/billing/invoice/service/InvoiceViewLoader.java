package com.red_escuela.backend.billing.invoice.service;

import com.red_escuela.backend.billing.entity.Payment;
import com.red_escuela.backend.billing.entity.PaymentAllocation;
import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.billing.repository.PaymentAllocationRepository;
import com.red_escuela.backend.enrollment.entity.CourseEnrollment;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.enrollment.repository.CourseEnrollmentRepository;
import com.red_escuela.backend.enrollment.repository.ProgramEnrollmentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class InvoiceViewLoader {

    private final ProgramEnrollmentRepository programEnrollmentRepository;
    private final CourseEnrollmentRepository courseEnrollmentRepository;
    private final PaymentAllocationRepository paymentAllocationRepository;
    private final InvoicePaymentShareSource paymentShareSource;

    public InvoiceViewLoader(ProgramEnrollmentRepository programEnrollmentRepository, CourseEnrollmentRepository courseEnrollmentRepository,
                             PaymentAllocationRepository paymentAllocationRepository, InvoicePaymentShareSource paymentShareSource) {
        this.programEnrollmentRepository = programEnrollmentRepository;
        this.courseEnrollmentRepository = courseEnrollmentRepository;
        this.paymentAllocationRepository = paymentAllocationRepository;
        this.paymentShareSource = paymentShareSource;
    }

    @Transactional(readOnly = true)
    public InvoiceView loadInvoiceView(Invoice invoice) {
        return loadInvoiceViews(List.of(invoice)).get(0);
    }

    @Transactional(readOnly = true)
    public List<InvoiceView> loadInvoiceViews(Collection<Invoice> invoices) {
        if (invoices.isEmpty()) {
            return List.of();
        }
        Map<Long, ProgramEnrollment> programs = programEnrollmentRepository.findAllById(invoices.stream()
                .flatMap(invoice -> invoice.findProgramEnrollmentId().stream()).distinct().toList()).stream()
                .collect(Collectors.toMap(ProgramEnrollment::getId, Function.identity()));
        Map<Long, CourseEnrollment> courses = courseEnrollmentRepository.findAllById(invoices.stream()
                .flatMap(invoice -> invoice.findCourseEnrollmentId().stream()).distinct().toList()).stream()
                .collect(Collectors.toMap(CourseEnrollment::getId, Function.identity()));
        List<Long> invoiceIds = invoices.stream().map(Invoice::getId).toList();
        Map<Long, List<InvoicePaymentShare>> shares = new HashMap<>(paymentShareSource.findCurrentSharesByInvoiceId(invoiceIds));
        paymentAllocationRepository.findByInvoiceIdInWithPayments(invoiceIds)
                .forEach(allocation -> shares.merge(allocation.getInvoice().getId(), List.of(legacyShareOf(allocation)),
                        (current, legacy) -> Stream.concat(current.stream(), legacy.stream()).toList()));
        return invoices.stream()
                .map(invoice -> new InvoiceView(invoice, invoice.findProgramEnrollmentId().map(programs::get).orElse(null),
                        invoice.findCourseEnrollmentId().map(courses::get).orElse(null), shares.getOrDefault(invoice.getId(), List.of())))
                .toList();
    }

    private static InvoicePaymentShare legacyShareOf(PaymentAllocation allocation) {
        Payment payment = allocation.getPayment();
        return new InvoicePaymentShare(payment.getId(), payment.getStatus().name(), payment.getMethod().name(),
                payment.getCardType() == null ? null : payment.getCardType().name(), payment.getCardInstallments(),
                allocation.getAmountApplied(), payment.getAmount(), payment.getPaidAt(), payment.getExternalReference(), payment.getNotes(),
                payment.getRegisteredBy().getName() + " " + payment.getRegisteredBy().getLastname());
    }

    @Transactional(readOnly = true)
    public Optional<ProgramEnrollment> findProgramEnrollment(Long programEnrollmentId) {
        return programEnrollmentRepository.findById(programEnrollmentId);
    }

    @Transactional(readOnly = true)
    public Optional<CourseEnrollment> findCourseEnrollment(Long courseEnrollmentId) {
        return courseEnrollmentRepository.findById(courseEnrollmentId);
    }

    @Transactional(readOnly = true)
    public List<ProgramEnrollment> findProgramEnrollmentsOfStudent(Long studentId) {
        return programEnrollmentRepository.findByStudentId(studentId);
    }

    @Transactional(readOnly = true)
    public List<CourseEnrollment> findCourseEnrollmentsOfStudent(Long studentId) {
        return courseEnrollmentRepository.findByStudentId(studentId);
    }
}
