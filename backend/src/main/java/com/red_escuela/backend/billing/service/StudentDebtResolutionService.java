package com.red_escuela.backend.billing.service;

import com.red_escuela.backend.entity.CourseEnrollment;
import com.red_escuela.backend.billing.entity.EnrollmentBillingStanding;
import com.red_escuela.backend.entity.EnrollmentStatus;
import com.red_escuela.backend.billing.entity.Invoice;
import com.red_escuela.backend.billing.entity.InvoiceAdjustmentType;
import com.red_escuela.backend.billing.entity.InvoiceStatus;
import com.red_escuela.backend.billing.entity.MonetaryAdjustmentValue;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.entity.ProgramEnrollment;
import com.red_escuela.backend.entity.ProgramEnrollmentStatus;
import com.red_escuela.backend.billing.exception.StudentInDebtException;
import com.red_escuela.backend.repository.CourseEnrollmentRepository;
import com.red_escuela.backend.billing.repository.InvoiceRepository;
import com.red_escuela.backend.repository.ProgramEnrollmentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class StudentDebtResolutionService {

    private final InvoiceRepository invoiceRepository;
    private final CourseEnrollmentRepository courseEnrollmentRepository;
    private final ProgramEnrollmentRepository programEnrollmentRepository;

    public StudentDebtResolutionService(InvoiceRepository invoiceRepository, CourseEnrollmentRepository courseEnrollmentRepository,
                                         ProgramEnrollmentRepository programEnrollmentRepository) {
        this.invoiceRepository = invoiceRepository;
        this.courseEnrollmentRepository = courseEnrollmentRepository;
        this.programEnrollmentRepository = programEnrollmentRepository;
    }

    public void verifyNoDebt(Long studentId) {
        if (invoiceRepository.existsByStudentIdAndStatus(studentId, InvoiceStatus.OVERDUE)) {
            throw new StudentInDebtException(studentId);
        }
    }

    @Transactional
    public void waiveDebtForProgram(Long studentId, Long programId, String reason, Person director) {
        List<ProgramEnrollment> oldCycles = programEnrollmentRepository.findByStudentIdAndProgramIdAndStatusAndBillingStanding(
                studentId, programId, ProgramEnrollmentStatus.WITHDRAWN, EnrollmentBillingStanding.WITHDRAWN_FOR_NONPAYMENT);
        for (ProgramEnrollment cycle : oldCycles) {
            for (Invoice invoice : invoiceRepository.findByProgramEnrollmentIdAndStatusIn(
                    cycle.getId(), List.of(InvoiceStatus.PENDING, InvoiceStatus.OVERDUE))) {
                waive(invoice, reason, director);
            }
        }
    }

    @Transactional
    public void waiveDebtForCourse(Long studentId, Long courseId, String reason, Person director) {
        List<CourseEnrollment> oldCycles = courseEnrollmentRepository.findByStudentIdAndCourseIdAndStatusAndBillingStanding(
                studentId, courseId, EnrollmentStatus.WITHDRAWN, EnrollmentBillingStanding.WITHDRAWN_FOR_NONPAYMENT);
        for (CourseEnrollment cycle : oldCycles) {
            for (Invoice invoice : invoiceRepository.findByCourseEnrollmentIdAndStatusIn(
                    cycle.getId(), List.of(InvoiceStatus.PENDING, InvoiceStatus.OVERDUE))) {
                waive(invoice, reason, director);
            }
        }
    }

    private void waive(Invoice invoice, String reason, Person director) {
        invoice.applyAdjustment(InvoiceAdjustmentType.WAIVE_DEBT,
                new MonetaryAdjustmentValue.FixedAmount(invoice.getRemainingBalance()), reason, director);
        invoiceRepository.save(invoice);
    }
}
