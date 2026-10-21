package com.red_escuela.backend.billing.invoice.service;

import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.enrollment.entity.CourseEnrollment;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.enrollment.repository.CourseEnrollmentRepository;
import com.red_escuela.backend.enrollment.repository.ProgramEnrollmentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class InvoiceViewLoader {

    private final ProgramEnrollmentRepository programEnrollmentRepository;
    private final CourseEnrollmentRepository courseEnrollmentRepository;
    private final InvoicePaymentShareSource paymentShareSource;
    private final InvoiceCreditNoteShareSource creditNoteShareSource;

    public InvoiceViewLoader(ProgramEnrollmentRepository programEnrollmentRepository, CourseEnrollmentRepository courseEnrollmentRepository,
                             InvoicePaymentShareSource paymentShareSource, InvoiceCreditNoteShareSource creditNoteShareSource) {
        this.programEnrollmentRepository = programEnrollmentRepository;
        this.courseEnrollmentRepository = courseEnrollmentRepository;
        this.paymentShareSource = paymentShareSource;
        this.creditNoteShareSource = creditNoteShareSource;
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
        Map<Long, List<InvoicePaymentShare>> shares = paymentShareSource.findCurrentSharesByInvoiceId(invoiceIds).resolvedByInvoiceId();
        Map<Long, List<InvoiceCreditNoteSourceShare>> creditNotes = creditNoteShareSource.findCurrentCreditNoteSharesByInvoiceId(invoiceIds);
        return invoices.stream()
                .map(invoice -> new InvoiceView(invoice, invoice.findProgramEnrollmentId().map(programs::get).orElse(null),
                        invoice.findCourseEnrollmentId().map(courses::get).orElse(null), shares.getOrDefault(invoice.getId(), List.of()),
                        creditNotes.getOrDefault(invoice.getId(), List.of())))
                .toList();
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
