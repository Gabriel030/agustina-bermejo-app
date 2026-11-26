package com.red_escuela.backend.enrollment.api;

import com.red_escuela.backend.enrollment.repository.EnrollmentFeeExemptionRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Transactional(readOnly = true)
public class EnrollmentFeeExemptionFinder {

    private final EnrollmentFeeExemptionRepository exemptionRepository;

    public EnrollmentFeeExemptionFinder(EnrollmentFeeExemptionRepository exemptionRepository) {
        this.exemptionRepository = exemptionRepository;
    }

    public boolean isExempted(Long programEnrollmentId, Long templateId, int slot) {
        return exemptionRepository.findByProgramEnrollmentIdAndTemplateIdAndSlotAndRevokedAtIsNull(programEnrollmentId, templateId, slot).isPresent();
    }
}
