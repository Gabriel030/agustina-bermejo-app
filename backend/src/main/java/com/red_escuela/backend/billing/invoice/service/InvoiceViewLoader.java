package com.red_escuela.backend.billing.invoice.service;

import com.red_escuela.backend.billing.entity.Payment;
import com.red_escuela.backend.billing.entity.PaymentStatus;
import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.billing.repository.PaymentRepository;
import com.red_escuela.backend.enrollment.entity.CourseEnrollment;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.enrollment.repository.CourseEnrollmentRepository;
import com.red_escuela.backend.enrollment.repository.ProgramEnrollmentRepository;
import com.red_escuela.backend.shared.domain.Money;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class InvoiceViewLoader {

    private final ProgramEnrollmentRepository programEnrollmentRepository;
    private final CourseEnrollmentRepository courseEnrollmentRepository;
    private final PaymentRepository legacyPaymentRepository;
    private final InvoicePaymentShareSource paymentShareSource;

    public InvoiceViewLoader(ProgramEnrollmentRepository programEnrollmentRepository, CourseEnrollmentRepository courseEnrollmentRepository,
                             PaymentRepository legacyPaymentRepository, InvoicePaymentShareSource paymentShareSource) {
        this.programEnrollmentRepository = programEnrollmentRepository;
        this.courseEnrollmentRepository = courseEnrollmentRepository;
        this.legacyPaymentRepository = legacyPaymentRepository;
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
        Map<Long, List<InvoicePaymentShare>> shares = sharesOf(paymentShareSource.findCurrentSharesByInvoiceId(invoiceIds));
        return invoices.stream()
                .map(invoice -> new InvoiceView(invoice, invoice.findProgramEnrollmentId().map(programs::get).orElse(null),
                        invoice.findCourseEnrollmentId().map(courses::get).orElse(null), shares.getOrDefault(invoice.getId(), List.of())))
                .toList();
    }

    private Map<Long, List<InvoicePaymentShare>> sharesOf(InvoicePaymentShares found) {
        Set<Long> legacyIds = found.unresolvedAppliedByPaymentIdByInvoiceId().values().stream().flatMap(applied -> applied.keySet().stream())
                .collect(Collectors.toSet());
        Map<Long, Payment> legacyPayments = legacyIds.isEmpty() ? Map.of() : legacyPaymentRepository.findAllWithRegisteredByByIdIn(legacyIds).stream()
                .collect(Collectors.toMap(Payment::getId, Function.identity()));
        Map<Long, List<InvoicePaymentShare>> shares = new HashMap<>(found.resolvedByInvoiceId());
        found.unresolvedAppliedByPaymentIdByInvoiceId().forEach((invoiceId, appliedByPayment) -> {
            List<InvoicePaymentShare> legacyShares = appliedByPayment.entrySet().stream().map(entry -> {
                Payment payment = legacyPayments.get(entry.getKey());
                if (payment == null) {
                    throw new IllegalStateException("Payment " + entry.getKey() + " referenced by an allocation source does not exist");
                }
                return payment.getStatus() == PaymentStatus.CONFIRMED ? legacyShareOf(payment, entry.getValue()) : null;
            }).filter(Objects::nonNull).toList();
            shares.merge(invoiceId, legacyShares, (current, legacy) -> Stream.concat(current.stream(), legacy.stream()).toList());
        });
        return shares;
    }

    private static InvoicePaymentShare legacyShareOf(Payment payment, Money applied) {
        return new InvoicePaymentShare(payment.getId(), InvoicePaymentShare.CONFIRMED, payment.getMethod().name(),
                payment.getCardType() == null ? null : payment.getCardType().name(), payment.getCardInstallments(),
                applied, payment.getAmount(), payment.getPaidAt(), payment.getExternalReference(), payment.getNotes(),
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
