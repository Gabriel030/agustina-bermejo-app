package com.red_escuela.backend.billing.service;

import com.red_escuela.backend.billing.configuration.service.BillingConfigurationReader;
import com.red_escuela.backend.billing.entity.EnrollmentPaymentStatus;
import com.red_escuela.backend.billing.invoice.domain.FeeType;
import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.billing.entity.PaymentPlanCadence;
import com.red_escuela.backend.billing.entity.PaymentPlanSelection;
import com.red_escuela.backend.billing.planning.domain.CourseInvoicePlanningFacts;
import com.red_escuela.backend.billing.invoice.repository.InvoiceRepository;
import com.red_escuela.backend.billing.repository.PaymentPlanSelectionRepository;
import com.red_escuela.backend.academicstructure.entity.Course;
import com.red_escuela.backend.enrollment.entity.CourseEnrollment;
import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.enrollment.exception.CourseEnrollmentNotFoundException;
import com.red_escuela.backend.enrollment.repository.CourseEnrollmentRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class CourseInvoicePlanningContextLoader {
    private final CourseEnrollmentRepository courseEnrollmentRepository;
    private final PaymentPlanSelectionRepository selectionRepository;
    private final InvoiceRepository invoiceRepository;
    private final BillingConfigurationReader configurationReader;

    public CourseInvoicePlanningContextLoader(CourseEnrollmentRepository courseEnrollmentRepository, PaymentPlanSelectionRepository selectionRepository,
                                     InvoiceRepository invoiceRepository, BillingConfigurationReader configurationReader) {
        this.courseEnrollmentRepository = courseEnrollmentRepository;
        this.selectionRepository = selectionRepository;
        this.invoiceRepository = invoiceRepository;
        this.configurationReader = configurationReader;
    }

    public record CourseContext(CourseInvoicePlanningFacts facts, PaymentPlanSelection selection, CourseEnrollment enrollment) {
    }

    public CourseContext load(Long enrollmentId, YearMonth month, LocalDate today) {
        CourseEnrollment enrollment = courseEnrollmentRepository.findById(enrollmentId)
                .orElseThrow(() -> new CourseEnrollmentNotFoundException(enrollmentId));
        Course course = enrollment.getCourse();
        PaymentPlanSelection selection = selectionRepository.findActiveForCourseEnrollment(enrollmentId, month).orElse(null);
        PaymentPlanCadence cadence = selection == null ? null : selection.getPaymentPlanOption().getCadence();
        List<Invoice> recurring = invoiceRepository.findByCourseEnrollmentIdOrderByPeriod(enrollmentId).stream()
                .filter(i -> i.getFeeType() == FeeType.MONTHLY_INSTALLMENT).toList();
        Set<YearMonth> periods = recurring.stream().map(Invoice::getPeriod).collect(Collectors.toSet());
        long inTerm = recurring.stream().filter(i -> course.getTerm().contains(i.getPeriod())).count();
        boolean suspended = enrollment.getPaymentStatus() == EnrollmentPaymentStatus.SUSPENDED
                || enrollment.getPaymentStatus() == EnrollmentPaymentStatus.WITHDRAWN_FOR_NONPAYMENT;
        CourseInvoicePlanningFacts facts = new CourseInvoicePlanningFacts(enrollmentId, month, today, enrollment.getStatus() == EnrollmentStatus.ENROLLED, suspended,
                selection != null, cadence, selection == null ? null : selection.getEffectiveFrom(), selection == null ? null : selection.getScopeMonths(),
                course.getTerm()::contains, course.getDurationMonths(), periods, inTerm, configurationReader.courseTermsEffectiveOn(course.getId(), today).monthlyInstallmentDueDays());
        return new CourseContext(facts, selection, enrollment);
    }
}
