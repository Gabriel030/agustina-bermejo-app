package com.red_escuela.backend.billing.usecase.scholarship;

import com.red_escuela.backend.billing.dto.CreateScholarshipRequest;
import com.red_escuela.backend.billing.entity.InvoiceStatus;
import com.red_escuela.backend.billing.entity.Scholarship;
import com.red_escuela.backend.billing.repository.InvoiceRepository;
import com.red_escuela.backend.billing.repository.ScholarshipRepository;
import com.red_escuela.backend.billing.service.ScholarshipGrantSupport;
import com.red_escuela.backend.enrollment.entity.CourseEnrollment;
import com.red_escuela.backend.enrollment.exception.CourseEnrollmentNotFoundException;
import com.red_escuela.backend.identity.service.CurrentPersonService;
import com.red_escuela.backend.enrollment.repository.CourseEnrollmentRepository;
import com.red_escuela.backend.service.RecurringFeeWindowService;
import com.red_escuela.backend.shared.UseCase;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@UseCase
public class GrantScholarshipForCourseService implements GrantScholarshipForCourseUseCase {

    private final ScholarshipRepository scholarshipRepository;
    private final CourseEnrollmentRepository courseEnrollmentRepository;
    private final RecurringFeeWindowService recurringFeeWindowService;
    private final CurrentPersonService currentPersonService;
    private final InvoiceRepository invoiceRepository;
    private final ScholarshipGrantSupport support;

    public GrantScholarshipForCourseService(ScholarshipRepository scholarshipRepository, CourseEnrollmentRepository courseEnrollmentRepository,
                                             RecurringFeeWindowService recurringFeeWindowService, CurrentPersonService currentPersonService,
                                             InvoiceRepository invoiceRepository, ScholarshipGrantSupport support) {
        this.scholarshipRepository = scholarshipRepository;
        this.courseEnrollmentRepository = courseEnrollmentRepository;
        this.recurringFeeWindowService = recurringFeeWindowService;
        this.currentPersonService = currentPersonService;
        this.invoiceRepository = invoiceRepository;
        this.support = support;
    }

    @Override
    @Transactional
    public Scholarship grant(Long courseEnrollmentId, CreateScholarshipRequest request) {
        CourseEnrollment enrollment = courseEnrollmentRepository.findById(courseEnrollmentId)
                .orElseThrow(() -> new CourseEnrollmentNotFoundException(courseEnrollmentId));
        support.validateInstallmentsWithinCohortSpan(request.installments(), recurringFeeWindowService.remainingInvoices(enrollment));
        Scholarship scholarship = support.build(request, currentPersonService.resolveCurrentPerson(), enrollment.getCourse().getCurrency());
        scholarship.setCourseEnrollment(enrollment);
        scholarship = scholarshipRepository.save(scholarship);
        support.applyRetroactiveCreditIfCurrentInvoiceAlreadyPaid(scholarship,
                invoiceRepository.findByCourseEnrollmentIdAndStatusIn(courseEnrollmentId, List.of(InvoiceStatus.PAID)),
                enrollment.getStudent());
        return scholarship;
    }
}
