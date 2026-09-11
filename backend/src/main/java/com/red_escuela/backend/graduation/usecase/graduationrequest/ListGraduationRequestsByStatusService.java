package com.red_escuela.backend.graduation.usecase.graduationrequest;

import com.red_escuela.backend.graduation.entity.GraduationRequest;
import com.red_escuela.backend.graduation.entity.GraduationRequestStatus;
import com.red_escuela.backend.graduation.repository.GraduationRequestRepository;
import com.red_escuela.backend.shared.UseCase;

import java.util.List;

@UseCase
public class ListGraduationRequestsByStatusService implements ListGraduationRequestsByStatusUseCase {

    private final GraduationRequestRepository graduationRequestRepository;

    public ListGraduationRequestsByStatusService(GraduationRequestRepository graduationRequestRepository) {
        this.graduationRequestRepository = graduationRequestRepository;
    }

    @Override
    public List<GraduationRequest> list(GraduationRequestStatus status) {
        return graduationRequestRepository.findByStatus(status);
    }
}
