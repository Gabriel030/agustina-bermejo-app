package com.red_escuela.backend.graduation.service;

import com.red_escuela.backend.enrollment.service.ProgramEnrollmentService;
import com.red_escuela.backend.graduation.entity.GraduationRequestStatus;
import com.red_escuela.backend.graduation.repository.GraduationRequestRepository;
import com.red_escuela.backend.graduation.usecase.graduationrequest.RequestGraduationUseCase;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GraduationRequestAutoRequester {

    private final ProgramEnrollmentService programEnrollmentService;
    private final GraduationRequestRepository graduationRequestRepository;
    private final RequestGraduationUseCase requestGraduationUseCase;

    public GraduationRequestAutoRequester(ProgramEnrollmentService programEnrollmentService,
                                           GraduationRequestRepository graduationRequestRepository,
                                           RequestGraduationUseCase requestGraduationUseCase) {
        this.programEnrollmentService = programEnrollmentService;
        this.graduationRequestRepository = graduationRequestRepository;
        this.requestGraduationUseCase = requestGraduationUseCase;
    }

    @Transactional
    public void createIfEligible(Long programEnrollmentId) {
        if (graduationRequestRepository.existsByProgramEnrollmentIdAndStatusNot(programEnrollmentId, GraduationRequestStatus.REJECTED)) {
            return;
        }
        if (!programEnrollmentService.calculateProgress(programEnrollmentId).elegibleParaCompletar()) {
            return;
        }
        requestGraduationUseCase.request(programEnrollmentId);
    }
}
