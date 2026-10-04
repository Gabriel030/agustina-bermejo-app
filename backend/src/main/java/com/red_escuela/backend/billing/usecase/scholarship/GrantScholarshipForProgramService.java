package com.red_escuela.backend.billing.usecase.scholarship;

import com.red_escuela.backend.billing.dto.CreateScholarshipRequest;
import com.red_escuela.backend.billing.entity.InvoiceStatus;
import com.red_escuela.backend.billing.entity.Scholarship;
import com.red_escuela.backend.billing.repository.InvoiceRepository;
import com.red_escuela.backend.billing.repository.ScholarshipRepository;
import com.red_escuela.backend.billing.service.ScholarshipGrantSupport;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.enrollment.exception.ProgramEnrollmentNotFoundException;
import com.red_escuela.backend.identity.service.CurrentPersonService;
import com.red_escuela.backend.enrollment.repository.ProgramEnrollmentRepository;
import com.red_escuela.backend.service.RecurringFeeWindowService;
import com.red_escuela.backend.shared.UseCase;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@UseCase
public class GrantScholarshipForProgramService implements GrantScholarshipForProgramUseCase {

    private final ScholarshipRepository scholarshipRepository;
    private final ProgramEnrollmentRepository programEnrollmentRepository;
    private final RecurringFeeWindowService recurringFeeWindowService;
    private final CurrentPersonService currentPersonService;
    private final InvoiceRepository invoiceRepository;
    private final ScholarshipGrantSupport support;

    public GrantScholarshipForProgramService(ScholarshipRepository scholarshipRepository, ProgramEnrollmentRepository programEnrollmentRepository,
                                              RecurringFeeWindowService recurringFeeWindowService, CurrentPersonService currentPersonService,
                                              InvoiceRepository invoiceRepository, ScholarshipGrantSupport support) {
        this.scholarshipRepository = scholarshipRepository;
        this.programEnrollmentRepository = programEnrollmentRepository;
        this.recurringFeeWindowService = recurringFeeWindowService;
        this.currentPersonService = currentPersonService;
        this.invoiceRepository = invoiceRepository;
        this.support = support;
    }

    @Override
    @Transactional
    public Scholarship grant(Long programEnrollmentId, CreateScholarshipRequest request) {
        ProgramEnrollment enrollment = programEnrollmentRepository.findById(programEnrollmentId)
                .orElseThrow(() -> new ProgramEnrollmentNotFoundException(programEnrollmentId));
        support.validateInstallmentsWithinCohortSpan(request.installments(), recurringFeeWindowService.remainingInvoices(enrollment));
        Scholarship scholarship = support.build(request, currentPersonService.resolveCurrentPerson(), enrollment.getProgram().getCurrency());
        scholarship.setProgramEnrollment(enrollment);
        scholarship = scholarshipRepository.save(scholarship);
        support.applyRetroactiveCreditIfCurrentInvoiceAlreadyPaid(scholarship,
                invoiceRepository.findByProgramEnrollmentIdAndStatusIn(programEnrollmentId, List.of(InvoiceStatus.PAID)),
                enrollment.getStudent());
        return scholarship;
    }
}
