package com.red_escuela.backend.enrollment.service;

import com.red_escuela.backend.enrollment.api.EnrollmentFeeRequirementPort;
import com.red_escuela.backend.enrollment.dto.EnrollmentFeeExemptionResponse;
import com.red_escuela.backend.enrollment.dto.EnrollmentFeeObligationResponse;
import com.red_escuela.backend.enrollment.repository.EnrollmentFeeExemptionRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
public class EnrollmentFeeReader {

    private final EnrollmentFeeRequirementPort requirementPort;
    private final EnrollmentFeeExemptionRepository exemptionRepository;

    public EnrollmentFeeReader(EnrollmentFeeRequirementPort requirementPort, EnrollmentFeeExemptionRepository exemptionRepository) {
        this.requirementPort = requirementPort;
        this.exemptionRepository = exemptionRepository;
    }

    @Transactional(readOnly = true)
    public List<EnrollmentFeeObligationResponse> readFeeStatus(Long programEnrollmentId) {
        return requirementPort.statusOf(programEnrollmentId).stream().map(EnrollmentFeeObligationResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<EnrollmentFeeExemptionResponse> listExemptions(Long programEnrollmentId) {
        return exemptionRepository.findByProgramEnrollmentIdOrderByGrantedAtDesc(programEnrollmentId).stream()
                .map(EnrollmentFeeExemptionResponse::from).toList();
    }
}
