package com.red_escuela.backend.enrollment.service;

import com.red_escuela.backend.billing.service.EnrollmentFeeRequirementService;
import com.red_escuela.backend.enrollment.dto.EnrollmentFeeExemptionResponse;
import com.red_escuela.backend.enrollment.dto.EnrollmentFeeObligationResponse;
import com.red_escuela.backend.enrollment.exception.ProgramEnrollmentNotFoundException;
import com.red_escuela.backend.enrollment.repository.EnrollmentFeeExemptionRepository;
import com.red_escuela.backend.enrollment.repository.ProgramEnrollmentRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
public class EnrollmentFeeReader {

    private final ProgramEnrollmentRepository programEnrollmentRepository;
    private final EnrollmentFeeRequirementService requirementService;
    private final EnrollmentFeeExemptionRepository exemptionRepository;

    public EnrollmentFeeReader(ProgramEnrollmentRepository programEnrollmentRepository,
                               EnrollmentFeeRequirementService requirementService,
                               EnrollmentFeeExemptionRepository exemptionRepository) {
        this.programEnrollmentRepository = programEnrollmentRepository;
        this.requirementService = requirementService;
        this.exemptionRepository = exemptionRepository;
    }

    @Transactional(readOnly = true)
    public List<EnrollmentFeeObligationResponse> readFeeStatus(Long programEnrollmentId) {
        return requirementService.statusOf(programEnrollmentRepository.findById(programEnrollmentId)
                .orElseThrow(() -> new ProgramEnrollmentNotFoundException(programEnrollmentId)))
                .stream().map(EnrollmentFeeObligationResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<EnrollmentFeeExemptionResponse> listExemptions(Long programEnrollmentId) {
        return exemptionRepository.findByProgramEnrollmentIdOrderByGrantedAtDesc(programEnrollmentId).stream()
                .map(EnrollmentFeeExemptionResponse::from).toList();
    }
}
