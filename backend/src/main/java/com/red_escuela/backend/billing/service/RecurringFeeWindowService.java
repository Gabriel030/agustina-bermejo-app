package com.red_escuela.backend.billing.service;

import com.red_escuela.backend.enrollment.entity.CourseEnrollment;
import com.red_escuela.backend.billing.entity.FeeType;
import com.red_escuela.backend.billing.entity.Invoice;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.billing.entity.ProgramYearBillingTemplate;
import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.enrollment.exception.CourseEnrollmentNotFoundException;
import com.red_escuela.backend.enrollment.exception.ProgramEnrollmentNotFoundException;
import com.red_escuela.backend.enrollment.repository.CourseEnrollmentRepository;
import com.red_escuela.backend.billing.repository.InvoiceRepository;
import com.red_escuela.backend.enrollment.repository.ProgramEnrollmentRepository;
import org.springframework.stereotype.Service;

import java.time.YearMonth;
import java.util.List;

@Service
public class RecurringFeeWindowService {
    private final InvoiceRepository invoiceRepository;
    private final ProgramEnrollmentRepository programEnrollmentRepository;
    private final CourseEnrollmentRepository courseEnrollmentRepository;

    public RecurringFeeWindowService(InvoiceRepository invoiceRepository, ProgramEnrollmentRepository programEnrollmentRepository,
                                        CourseEnrollmentRepository courseEnrollmentRepository) {
        this.invoiceRepository = invoiceRepository;
        this.programEnrollmentRepository = programEnrollmentRepository;
        this.courseEnrollmentRepository = courseEnrollmentRepository;
    }

    public boolean canBeBilled(ProgramEnrollment programEnrollment, YearMonth period) {
        ProgramYearBillingTemplate template = currentTemplate(programEnrollment);
        long generated = recurringFeeInvoicesGeneratedInWindow(
                invoiceRepository.findByProgramEnrollmentIdOrderByPeriod(programEnrollment.getId()), template.getTerm());
        return template.canGenerateInvoice(period, generated);
    }

    public int remainingInvoices(ProgramEnrollment programEnrollment) {
        ProgramYearBillingTemplate template = currentTemplate(programEnrollment);
        long generated = recurringFeeInvoicesGeneratedInWindow(
                invoiceRepository.findByProgramEnrollmentIdOrderByPeriod(programEnrollment.getId()), template.getTerm());
        return template.remainingInvoices(generated);
    }

    public boolean canBeBilled(CourseEnrollment courseEnrollment, YearMonth period) {
        var course = managedCourseEnrollment(courseEnrollment).getCourse();
        long generated = recurringFeeInvoicesGeneratedInWindow(
                invoiceRepository.findByCourseEnrollmentIdOrderByPeriod(courseEnrollment.getId()), course.getTerm());
        return course.canGenerateInvoice(period, generated);
    }

    public Integer remainingInvoices(CourseEnrollment courseEnrollment) {
        var course = managedCourseEnrollment(courseEnrollment).getCourse();
        long generated = recurringFeeInvoicesGeneratedInWindow(
                invoiceRepository.findByCourseEnrollmentIdOrderByPeriod(courseEnrollment.getId()), course.getTerm());
        return course.remainingInvoices(generated);
    }

    private ProgramYearBillingTemplate currentTemplate(ProgramEnrollment programEnrollment) {
        ProgramEnrollment managed = programEnrollmentRepository.findById(programEnrollment.getId())
                .orElseThrow(() -> new ProgramEnrollmentNotFoundException(programEnrollment.getId()));
        return managed.getCurrentBillingTemplate() != null
                ? managed.getCurrentBillingTemplate()
                : managed.getProgramCohort().getTemplate();
    }

    private CourseEnrollment managedCourseEnrollment(CourseEnrollment courseEnrollment) {
        return courseEnrollmentRepository.findById(courseEnrollment.getId())
                .orElseThrow(() -> new CourseEnrollmentNotFoundException(courseEnrollment.getId()));
    }

    private long recurringFeeInvoicesGeneratedInWindow(List<Invoice> invoices, Term term) {
        return invoices.stream()
                .filter(i -> i.getFeeType() == FeeType.RECURRING_FEE)
                .filter(i -> term.contains(i.getPeriod()))
                .count();
    }
}
