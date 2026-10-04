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

import java.time.Clock;
import java.time.YearMonth;
import com.red_escuela.backend.billing.planning.YearCycle;
import com.red_escuela.backend.billing.planning.YearCycles;
import com.red_escuela.backend.billing.repository.ProgramYearBillingTemplateRepository;
import java.util.List;

@Service
public class RecurringFeeWindowService {
    private final InvoiceRepository invoiceRepository;
    private final ProgramEnrollmentRepository programEnrollmentRepository;
    private final CourseEnrollmentRepository courseEnrollmentRepository;
    private final ProgramYearBillingTemplateRepository templateRepository;
    private final Clock clock;

    public RecurringFeeWindowService(InvoiceRepository invoiceRepository, ProgramEnrollmentRepository programEnrollmentRepository,
                                        CourseEnrollmentRepository courseEnrollmentRepository,
                                        ProgramYearBillingTemplateRepository templateRepository, Clock clock) {
        this.templateRepository = templateRepository;
        this.clock = clock;
        this.invoiceRepository = invoiceRepository;
        this.programEnrollmentRepository = programEnrollmentRepository;
        this.courseEnrollmentRepository = courseEnrollmentRepository;
    }

    public boolean canBeBilled(ProgramEnrollment programEnrollment, YearMonth period) {
        ProgramYearBillingTemplate template = templateFor(programEnrollment, period);
        if (template == null) {
            return false;
        }
        YearCycle cycle = YearCycle.of(template, period.getYear());
        long generated = generatedInCycle(programEnrollment, cycle);
        return cycle.installmentRangeContains(period) && generated < template.getRecurringFeeInvoicesCount();
    }

    public int remainingInvoices(ProgramEnrollment programEnrollment) {
        ProgramYearBillingTemplate template = templateFor(programEnrollment, YearMonth.now(clock));
        if (template == null) {
            template = fallbackTemplate(programEnrollment);
        }
        if (template == null) {
            return 0;
        }
        return template.remainingInvoices(generatedInCycle(programEnrollment, YearCycle.of(template, YearMonth.now(clock).getYear())));
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

    private long generatedInCycle(ProgramEnrollment programEnrollment, YearCycle cycle) {
        return invoiceRepository.findByProgramEnrollmentIdOrderByPeriod(programEnrollment.getId()).stream()
                .filter(i -> i.getFeeType() == FeeType.RECURRING_FEE)
                .filter(i -> cycle.installmentRangeContains(i.getPeriod()))
                .count();
    }

    private ProgramYearBillingTemplate templateFor(ProgramEnrollment programEnrollment, YearMonth period) {
        ProgramEnrollment managed = managedProgramEnrollment(programEnrollment);
        Long hint = managed.getCurrentBillingTemplate() != null ? managed.getCurrentBillingTemplate().getId() : null;
        YearCycles cycles = new YearCycles(
                templateRepository.findAllByProgramId(managed.getProgram().getId()).stream().map(t -> YearCycle.of(t, period.getYear())).toList(), hint);
        return cycles.forInstallment(period).map(YearCycle::template).orElse(null);
    }

    private ProgramYearBillingTemplate fallbackTemplate(ProgramEnrollment programEnrollment) {
        ProgramEnrollment managed = managedProgramEnrollment(programEnrollment);
        if (managed.getCurrentBillingTemplate() != null) {
            return managed.getCurrentBillingTemplate();
        }
        return managed.getProgramCohort() != null ? managed.getProgramCohort().getTemplate() : null;
    }

    private ProgramEnrollment managedProgramEnrollment(ProgramEnrollment programEnrollment) {
        return programEnrollmentRepository.findById(programEnrollment.getId())
                .orElseThrow(() -> new ProgramEnrollmentNotFoundException(programEnrollment.getId()));
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
