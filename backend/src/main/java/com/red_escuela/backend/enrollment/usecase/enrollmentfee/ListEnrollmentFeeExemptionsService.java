package com.red_escuela.backend.enrollment.usecase.enrollmentfee;

import com.red_escuela.backend.enrollment.dto.EnrollmentFeeExemptionResponse;
import com.red_escuela.backend.enrollment.repository.EnrollmentFeeExemptionRepository;
import com.red_escuela.backend.shared.UseCase;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@UseCase
public class ListEnrollmentFeeExemptionsService implements ListEnrollmentFeeExemptionsUseCase {
    private final EnrollmentFeeExemptionRepository exemptionRepository;

    public ListEnrollmentFeeExemptionsService(EnrollmentFeeExemptionRepository exemptionRepository) {
        this.exemptionRepository = exemptionRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<EnrollmentFeeExemptionResponse> list(Long programEnrollmentId) {
        return exemptionRepository.findByProgramEnrollmentIdOrderByGrantedAtDesc(programEnrollmentId).stream()
                .map(EnrollmentFeeExemptionResponse::from).toList();
    }
}
