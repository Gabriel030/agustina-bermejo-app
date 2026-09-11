package com.red_escuela.backend.graduation.usecase.graduationrequest;

import com.red_escuela.backend.graduation.entity.GraduationRequest;
import com.red_escuela.backend.graduation.repository.GraduationRequestRepository;
import com.red_escuela.backend.shared.UseCase;

import java.util.List;

@UseCase
public class ListGraduationRequestsByProgramEnrollmentService implements ListGraduationRequestsByProgramEnrollmentUseCase {

    private final GraduationRequestRepository graduationRequestRepository;

    public ListGraduationRequestsByProgramEnrollmentService(GraduationRequestRepository graduationRequestRepository) {
        this.graduationRequestRepository = graduationRequestRepository;
    }

    @Override
    public List<GraduationRequest> list(Long programEnrollmentId) {
        return graduationRequestRepository.findByProgramEnrollmentId(programEnrollmentId);
    }
}
