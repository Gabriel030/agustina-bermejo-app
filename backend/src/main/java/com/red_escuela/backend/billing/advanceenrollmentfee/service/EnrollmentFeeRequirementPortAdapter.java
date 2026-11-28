package com.red_escuela.backend.billing.advanceenrollmentfee.service;

import com.red_escuela.backend.academicstructure.api.AcademicReferenceFinder;
import com.red_escuela.backend.billing.planning.domain.EnrollmentFeeRequirementScope;
import com.red_escuela.backend.billing.service.EnrollmentFeeRequirementService;
import com.red_escuela.backend.enrollment.api.EnrollmentFeeObligationView;
import com.red_escuela.backend.enrollment.api.EnrollmentFeeRequirementPort;
import com.red_escuela.backend.enrollment.api.EnrollmentFeeRequirementPurpose;
import com.red_escuela.backend.enrollment.api.ProgramEnrollmentFinder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
public class EnrollmentFeeRequirementPortAdapter implements EnrollmentFeeRequirementPort {

    private final ProgramEnrollmentFinder programEnrollmentFinder;
    private final AcademicReferenceFinder academicReferenceFinder;
    private final EnrollmentFeeRequirementService requirementService;

    public EnrollmentFeeRequirementPortAdapter(ProgramEnrollmentFinder programEnrollmentFinder, AcademicReferenceFinder academicReferenceFinder,
                                               EnrollmentFeeRequirementService requirementService) {
        this.programEnrollmentFinder = programEnrollmentFinder;
        this.academicReferenceFinder = academicReferenceFinder;
        this.requirementService = requirementService;
    }

    @Override
    @Transactional
    public void requireForTerm(Long programEnrollmentId, Long termId, EnrollmentFeeRequirementPurpose purpose) {
        requirementService.requireForTerm(programEnrollmentFinder.requireById(programEnrollmentId), academicReferenceFinder.findTerm(termId), scopeOf(purpose));
    }

    @Override
    @Transactional
    public void requireForCurrentPeriod(Long programEnrollmentId, EnrollmentFeeRequirementPurpose purpose) {
        requirementService.requireForCurrentPeriod(programEnrollmentFinder.requireById(programEnrollmentId), scopeOf(purpose));
    }

    @Override
    @Transactional(readOnly = true)
    public List<EnrollmentFeeObligationView> statusOf(Long programEnrollmentId) {
        return requirementService.statusOf(programEnrollmentFinder.requireById(programEnrollmentId)).stream()
                .map(obligation -> new EnrollmentFeeObligationView(obligation.templateId(), obligation.slot(), obligation.slotCount(), obligation.periodFrom(),
                        obligation.periodTo(), obligation.statusName(), obligation.invoiceId(), obligation.dueDate(), obligation.remainingBalance(),
                        obligation.satisfied(), obligation.advance()))
                .toList();
    }

    private EnrollmentFeeRequirementScope scopeOf(EnrollmentFeeRequirementPurpose purpose) {
        return switch (purpose) {
            case SUBJECT_ENROLLMENT -> EnrollmentFeeRequirementScope.SUBJECT_ENROLLMENT;
            case EXAM_REGISTRATION -> EnrollmentFeeRequirementScope.EXAM_REGISTRATION;
        };
    }
}
