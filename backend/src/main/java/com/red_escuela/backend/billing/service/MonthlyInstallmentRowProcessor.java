package com.red_escuela.backend.billing.service;

import com.red_escuela.backend.billing.entity.FeeType;
import com.red_escuela.backend.billing.repository.InvoiceRepository;
import com.red_escuela.backend.enrollment.entity.CourseEnrollment;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.enrollment.exception.CourseEnrollmentNotFoundException;
import com.red_escuela.backend.enrollment.exception.ProgramEnrollmentNotFoundException;
import com.red_escuela.backend.enrollment.repository.CourseEnrollmentRepository;
import com.red_escuela.backend.enrollment.repository.ProgramEnrollmentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.YearMonth;

@Service
public class MonthlyInstallmentRowProcessor {
    private final InvoiceGenerationService invoiceGenerationService;
    private final InvoiceRepository invoiceRepository;
    private final ProgramEnrollmentRepository programEnrollmentRepository;
    private final CourseEnrollmentRepository courseEnrollmentRepository;

    public MonthlyInstallmentRowProcessor(InvoiceGenerationService invoiceGenerationService, InvoiceRepository invoiceRepository,
                                           ProgramEnrollmentRepository programEnrollmentRepository,
                                           CourseEnrollmentRepository courseEnrollmentRepository) {
        this.invoiceGenerationService = invoiceGenerationService;
        this.invoiceRepository = invoiceRepository;
        this.programEnrollmentRepository = programEnrollmentRepository;
        this.courseEnrollmentRepository = courseEnrollmentRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public InstallmentIssuanceOutcome issueForProgramEnrollment(Long enrollmentId, YearMonth month) {
        ProgramEnrollment enrollment = programEnrollmentRepository.findById(enrollmentId)
                .orElseThrow(() -> new ProgramEnrollmentNotFoundException(enrollmentId));
        boolean enrollmentMonth = enrollment.isEnrollmentFeeMonth(month);
        FeeType feeType = enrollmentMonth ? FeeType.ENROLLMENT_FEE : FeeType.RECURRING_FEE;
        if (invoiceRepository.existsByProgramEnrollmentIdAndFeeTypeAndPeriod(enrollmentId, feeType, month)) {
            return InstallmentIssuanceOutcome.SKIPPED;
        }
        if (enrollmentMonth) {
            invoiceGenerationService.generateEnrollmentFeeForProgram(enrollment, month);
        } else {
            invoiceGenerationService.generateNextInstallmentForProgram(enrollment, month);
        }
        return outcome(invoiceRepository.existsByProgramEnrollmentIdAndFeeTypeAndPeriod(enrollmentId, feeType, month));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public InstallmentIssuanceOutcome issueForCourseEnrollment(Long enrollmentId, YearMonth month) {
        CourseEnrollment enrollment = courseEnrollmentRepository.findById(enrollmentId)
                .orElseThrow(() -> new CourseEnrollmentNotFoundException(enrollmentId));
        if (invoiceRepository.existsByCourseEnrollmentIdAndFeeTypeAndPeriod(enrollmentId, FeeType.RECURRING_FEE, month)) {
            return InstallmentIssuanceOutcome.SKIPPED;
        }
        invoiceGenerationService.generateNextInstallmentForCourse(enrollment, month);
        return outcome(invoiceRepository.existsByCourseEnrollmentIdAndFeeTypeAndPeriod(enrollmentId, FeeType.RECURRING_FEE, month));
    }

    private InstallmentIssuanceOutcome outcome(boolean issued) {
        return issued ? InstallmentIssuanceOutcome.ISSUED : InstallmentIssuanceOutcome.SKIPPED;
    }
}
