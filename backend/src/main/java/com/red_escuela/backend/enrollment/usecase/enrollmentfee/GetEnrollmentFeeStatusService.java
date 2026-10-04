package com.red_escuela.backend.enrollment.usecase.enrollmentfee;

import com.red_escuela.backend.billing.service.EnrollmentFeeRequirementService;
import com.red_escuela.backend.enrollment.dto.EnrollmentFeeObligationResponse;
import com.red_escuela.backend.enrollment.exception.ProgramEnrollmentNotFoundException;
import com.red_escuela.backend.enrollment.repository.ProgramEnrollmentRepository;
import com.red_escuela.backend.shared.UseCase;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@UseCase
public class GetEnrollmentFeeStatusService implements GetEnrollmentFeeStatusUseCase {
    private final ProgramEnrollmentRepository programEnrollmentRepository;
    private final EnrollmentFeeRequirementService requirementService;

    public GetEnrollmentFeeStatusService(ProgramEnrollmentRepository programEnrollmentRepository, EnrollmentFeeRequirementService requirementService) {
        this.programEnrollmentRepository = programEnrollmentRepository;
        this.requirementService = requirementService;
    }

    @Override
    @Transactional(readOnly = true)
    public List<EnrollmentFeeObligationResponse> get(Long programEnrollmentId) {
        return requirementService.statusOf(programEnrollmentRepository.findById(programEnrollmentId)
                .orElseThrow(() -> new ProgramEnrollmentNotFoundException(programEnrollmentId)))
                .stream().map(EnrollmentFeeObligationResponse::from).toList();
    }
}
