package com.red_escuela.backend.billing.invoice.service;

import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.enrollment.entity.CourseEnrollment;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.enrollment.api.CourseEnrollmentFinder;
import com.red_escuela.backend.enrollment.api.ProgramEnrollmentFinder;
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

    private final ProgramEnrollmentFinder programEnrollmentFinder;
    private final CourseEnrollmentFinder courseEnrollmentFinder;
    private final InvoicePaymentShareSource paymentShareSource;
    private final InvoiceCreditNoteShareSource creditNoteShareSource;

    public InvoiceViewLoader(ProgramEnrollmentFinder programEnrollmentFinder, CourseEnrollmentFinder courseEnrollmentFinder,
                             InvoicePaymentShareSource paymentShareSource, InvoiceCreditNoteShareSource creditNoteShareSource) {
        this.programEnrollmentFinder = programEnrollmentFinder;
        this.courseEnrollmentFinder = courseEnrollmentFinder;
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
        Map<Long, ProgramEnrollment> programs = programEnrollmentFinder.findAllWithStudentAndProgramByIds(invoices.stream()
                .flatMap(invoice -> invoice.findProgramEnrollmentId().stream()).distinct().toList()).stream()
                .collect(Collectors.toMap(ProgramEnrollment::getId, Function.identity()));
        Map<Long, CourseEnrollment> courses = courseEnrollmentFinder.findAllWithStudentAndCourseByIds(invoices.stream()
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
        return programEnrollmentFinder.findById(programEnrollmentId);
    }

    @Transactional(readOnly = true)
    public Optional<CourseEnrollment> findCourseEnrollment(Long courseEnrollmentId) {
        return courseEnrollmentFinder.findById(courseEnrollmentId);
    }

    @Transactional(readOnly = true)
    public List<ProgramEnrollment> findProgramEnrollmentsOfStudent(Long studentId) {
        return programEnrollmentFinder.findByStudent(studentId);
    }

    @Transactional(readOnly = true)
    public List<CourseEnrollment> findCourseEnrollmentsOfStudent(Long studentId) {
        return courseEnrollmentFinder.findByStudent(studentId);
    }
}
